package top.jiangmok.operationlog.service;

import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.model.OperationLogPageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志存储服务接口
 *
 * @author mok
 */
public interface OperationLogService {

    /** 保存操作日志 */
    void saveOperationLog(OperationLogEntity entity);

    /** 按 ID 查询 */
    OperationLogEntity findById(String id);

    /** 幂等性检查：判断日志是否已存在 */
    boolean checkExistsById(String id);

    /** 清理指定日期前的日志，返回删除条数 */
    int cleanLogsBefore(LocalDateTime dateTime);

    /** 按 ID 删除 */
    void deleteById(String id);

    /**
     * 分页查询
     *
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页条数
     * @param keyword    关键词（模糊匹配 title/operatorName/operUrl）
     * @param conditions 额外查询条件（如 status、businessType、startTime、endTime）
     * @return 日志列表
     */
    List<OperationLogEntity> pageQuery(int pageNum, int pageSize,
                                       String keyword, Map<String, Object> conditions);

    /**
     * 带总记录数的分页查询。
     * <p>
     * 默认实现用于兼容已有自定义存储；Starter 内置存储会返回准确总数。
     * </p>
     */
    default OperationLogPageResult pageQueryResult(
            int pageNum, int pageSize, String keyword, Map<String, Object> conditions) {
        List<OperationLogEntity> records = pageQuery(pageNum, pageSize, keyword, conditions);
        return new OperationLogPageResult(records, records.size(), pageNum, pageSize);
    }
}
