package top.jiangmok.operationlog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.service.OperationLogService;
import top.jiangmok.operationlog.util.IdGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操作日志文件存储实现（JSON Lines）
 * 零外部依赖，基于 JDK 文件 IO + Jackson。
 * 追加写入极快，按天滚动，支持分页查询和条件过滤。
 *
 * @author mok
 */
public class OperationLogFileServiceImpl implements OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogFileServiceImpl.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String FILE_PREFIX = "operation-log-";
    private static final String FILE_SUFFIX = ".jsonl";

    private final OperationLogProperties properties;
    private final ObjectMapper objectMapper;
    private final Path logDir;

    /** 每文件一个锁对象，保证并发追加写入安全 */
    private final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();

    public OperationLogFileServiceImpl(OperationLogProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.logDir = Paths.get(properties.getFile().getLogDir()).toAbsolutePath();
        try {
            Files.createDirectories(logDir);
            log.info("操作日志文件存储目录: {}", logDir);
        } catch (IOException e) {
            log.error("无法创建日志目录: {}", logDir, e);
        }
    }

    // ==================== 写入 ====================

    @Override
    public void saveOperationLog(OperationLogEntity entity) {
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            return;
        }
        try {
            // 补全必要字段
            if (entity.getId() == null || entity.getId().isEmpty()) {
                entity.setId(IdGenerator.generate());
            }
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now());
            }
            if (entity.getOperTime() == null) {
                entity.setOperTime(LocalDateTime.now());
            }

            // 截断过长内容
            truncateIfNeeded(entity);

            // 追加写入 JSONL
            String json = objectMapper.writeValueAsString(entity);
            Path filePath = resolveFile(LocalDate.now());

            synchronized (getLock(filePath)) {
                Files.writeString(filePath, json + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }

            log.debug("操作日志已写入文件: {} - {}", entity.getTitle(), entity.getOperatorName());

        } catch (IOException e) {
            log.error("写入操作日志文件失败", e);
        }
    }

    // ==================== 查询 ====================

    @Override
    public OperationLogEntity findById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (Path file : listLogFilesDesc()) {
            OperationLogEntity found = scanFileForId(file, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public boolean checkExistsById(String id) {
        return findById(id) != null;
    }

    @Override
    public List<OperationLogEntity> pageQuery(int pageNum, int pageSize,
                                               String keyword, Map<String, Object> conditions) {
        List<OperationLogEntity> results = new ArrayList<>();
        int skip = (pageNum - 1) * pageSize;
        int skipped = 0;

        // 确定需要扫描的文件范围
        List<Path> filesToScan = filterFilesByTimeRange(listLogFilesDesc(), conditions);

        outer:
        for (Path file : filesToScan) {
            List<String> lines = readAllLinesSafe(file);
            // 倒序遍历（文件末尾是最新记录，自然 DESC）
            for (int i = lines.size() - 1; i >= 0; i--) {
                OperationLogEntity entity = parseLine(lines.get(i));
                if (entity == null) {
                    continue;
                }
                if (!matchesConditions(entity, keyword, conditions)) {
                    continue;
                }
                if (skipped < skip) {
                    skipped++;
                } else {
                    results.add(entity);
                    if (results.size() >= pageSize) {
                        break outer;
                    }
                }
            }
        }

        return results;
    }

    // ==================== 删除 ====================

    @Override
    public void deleteById(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        // 找到包含该 ID 的文件，重写（过滤掉被删除的条目）
        for (Path file : listLogFilesDesc()) {
            List<String> lines = readAllLinesSafe(file);
            List<String> filtered = new ArrayList<>();
            boolean found = false;

            for (String line : lines) {
                OperationLogEntity entity = parseLine(line);
                if (entity != null && id.equals(entity.getId())) {
                    found = true;
                    // 跳过此行，即删除
                } else {
                    filtered.add(line);
                }
            }

            if (found) {
                try {
                    synchronized (getLock(file)) {
                        Files.write(file, filtered, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                    log.debug("已从文件中删除日志: id={}, file={}", id, file.getFileName());
                } catch (IOException e) {
                    log.error("删除日志失败: id={}", id, e);
                }
                return;
            }
        }
        log.debug("未找到要删除的日志: id={}", id);
    }

    @Override
    public int cleanLogsBefore(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        LocalDate threshold = dateTime.toLocalDate();
        int deletedCount = 0;

        for (Path file : listLogFilesDesc()) {
            LocalDate fileDate = parseDateFromFileName(file);
            if (fileDate != null && fileDate.isBefore(threshold)) {
                try {
                    long lineCount = Files.lines(file).count();
                    Files.deleteIfExists(file);
                    deletedCount += (int) lineCount;
                    log.info("已清理过期日志文件: {} ({} 条)", file.getFileName(), lineCount);
                } catch (IOException e) {
                    log.error("清理日志文件失败: {}", file.getFileName(), e);
                }
            }
        }

        return deletedCount;
    }

    // ==================== 内部工具方法 ====================

    /** 确定今天的日志文件路径 */
    private Path resolveFile(LocalDate date) {
        if ("none".equalsIgnoreCase(properties.getFile().getRollover())) {
            return logDir.resolve(FILE_PREFIX + "all" + FILE_SUFFIX);
        }
        return logDir.resolve(FILE_PREFIX + date.format(DATE_FMT) + FILE_SUFFIX);
    }

    /** 列出所有日志文件，按日期 DESC */
    private List<Path> listLogFilesDesc() {
        try (var stream = Files.list(logDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(FILE_SUFFIX))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            log.error("列出日志文件失败", e);
            return List.of();
        }
    }

    /** 根据时间条件缩小扫描范围 */
    private List<Path> filterFilesByTimeRange(List<Path> files, Map<String, Object> conditions) {
        if (conditions == null) {
            return files;
        }
        LocalDate startDate = null;
        LocalDate endDate = null;

        Object startObj = conditions.get("startTime");
        if (startObj instanceof LocalDateTime st) {
            startDate = st.toLocalDate();
        }
        Object endObj = conditions.get("endTime");
        if (endObj instanceof LocalDateTime et) {
            endDate = et.toLocalDate();
        }

        if (startDate == null && endDate == null) {
            return files;
        }

        List<Path> filtered = new ArrayList<>();
        for (Path file : files) {
            LocalDate fileDate = parseDateFromFileName(file);
            if (fileDate == null) {
                // 无法解析日期的文件（如 none 模式的单文件），保留
                filtered.add(file);
                continue;
            }
            if (startDate != null && fileDate.isBefore(startDate)) {
                continue;
            }
            if (endDate != null && fileDate.isAfter(endDate)) {
                continue;
            }
            filtered.add(file);
        }
        return filtered;
    }

    /** 从文件名解析日期 */
    private LocalDate parseDateFromFileName(Path file) {
        String name = file.getFileName().toString();
        if (!name.startsWith(FILE_PREFIX) || !name.endsWith(FILE_SUFFIX)) {
            return null;
        }
        try {
            String dateStr = name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length());
            return LocalDate.parse(dateStr, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /** 在指定文件中查找 ID */
    private OperationLogEntity scanFileForId(Path file, String id) {
        for (String line : readAllLinesSafe(file)) {
            OperationLogEntity entity = parseLine(line);
            if (entity != null && id.equals(entity.getId())) {
                return entity;
            }
        }
        return null;
    }

    /** 条件过滤 */
    private boolean matchesConditions(OperationLogEntity entity, String keyword,
                                       Map<String, Object> conditions) {
        // 关键词模糊匹配（title / operatorName / operUrl）
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            boolean kwMatch = false;
            if (entity.getTitle() != null && entity.getTitle().toLowerCase().contains(kw)) kwMatch = true;
            if (entity.getOperatorName() != null && entity.getOperatorName().toLowerCase().contains(kw)) kwMatch = true;
            if (entity.getOperUrl() != null && entity.getOperUrl().toLowerCase().contains(kw)) kwMatch = true;
            if (!kwMatch) return false;
        }

        if (conditions == null) {
            return true;
        }

        // status 过滤
        Object statusObj = conditions.get("status");
        if (statusObj != null && !"".equals(statusObj.toString())) {
            int status = Integer.parseInt(statusObj.toString());
            if (entity.getStatus() == null || entity.getStatus() != status) return false;
        }

        // businessType 过滤
        Object businessTypeObj = conditions.get("businessType");
        if (businessTypeObj != null && !"".equals(businessTypeObj.toString())) {
            if (!businessTypeObj.toString().equals(entity.getBusinessType())) return false;
        }

        // startTime 过滤（精确到日期，由 filterFilesByTimeRange 做粗过滤，这里精过滤）
        Object startTime = conditions.get("startTime");
        if (startTime instanceof LocalDateTime st) {
            if (entity.getOperTime() != null && entity.getOperTime().isBefore(st)) return false;
        }

        // endTime 过滤
        Object endTime = conditions.get("endTime");
        if (endTime instanceof LocalDateTime et) {
            if (entity.getOperTime() != null && entity.getOperTime().isAfter(et)) return false;
        }

        return true;
    }

    /** 截断过长参数/响应内容 */
    private void truncateIfNeeded(OperationLogEntity entity) {
        int maxLen = properties.getMaxContentLength();
        if (entity.getOperParam() != null && entity.getOperParam().length() > maxLen) {
            entity.setOperParam(entity.getOperParam().substring(0, maxLen) + "...");
        }
        if (entity.getJsonResult() != null && entity.getJsonResult().length() > maxLen) {
            entity.setJsonResult(entity.getJsonResult().substring(0, maxLen) + "...");
        }
    }

    /** 安全读取文件所有行 */
    private List<String> readAllLinesSafe(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            log.error("读取日志文件失败: {}", file, e);
            return List.of();
        }
    }

    /** 解析一行 JSON */
    private OperationLogEntity parseLine(String line) {
        try {
            return objectMapper.readValue(line, OperationLogEntity.class);
        } catch (Exception e) {
            log.debug("跳过无效 JSON 行: {}", line.length() > 100 ? line.substring(0, 100) + "..." : line);
            return null;
        }
    }

    /** 获取文件对应的锁对象 */
    private Object getLock(Path file) {
        return fileLocks.computeIfAbsent(file.getFileName().toString(), k -> new Object());
    }
}
