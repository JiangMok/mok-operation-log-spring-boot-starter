package top.jiangmok.operationlog.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.mapper.OperationLogMapper;
import top.jiangmok.operationlog.service.OperationLogService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 MySQL 存储实现
 * <p>
 * 基于 MyBatis-Plus，用户需确保数据源已配置且表已创建。
 * </p>
 *
 * @author mok
 */
public class OperationLogMySqlServiceImpl
        extends ServiceImpl<OperationLogMapper, OperationLogEntity>
        implements OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogMySqlServiceImpl.class);

    private final OperationLogProperties properties;

    public OperationLogMySqlServiceImpl(OperationLogProperties properties) {
        this.properties = properties;
    }

    @Override
    @Transactional
    public void saveOperationLog(OperationLogEntity entity) {
        if (Boolean.FALSE.equals(properties.getEnabled())) {
            return;
        }
        try {
            // 限制参数长度
            if (entity.getOperParam() != null
                    && entity.getOperParam().length() > properties.getMaxContentLength()) {
                entity.setOperParam(entity.getOperParam()
                        .substring(0, properties.getMaxContentLength()) + "...");
            }
            if (entity.getJsonResult() != null
                    && entity.getJsonResult().length() > properties.getMaxContentLength()) {
                entity.setJsonResult(entity.getJsonResult()
                        .substring(0, properties.getMaxContentLength()) + "...");
            }

            entity.setOperTime(LocalDateTime.now());
            entity.setId(IdUtil.simpleUUID());
            save(entity);
            log.debug("操作日志已记录：{} - {}", entity.getTitle(), entity.getOperatorName());
        } catch (Exception e) {
            log.error("记录操作日志失败", e);
        }
    }

    @Override
    public OperationLogEntity findById(String id) {
        return baseMapper.selectById(id);
    }

    @Override
    public boolean checkExistsById(String id) {
        return baseMapper.selectById(id) != null;
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        baseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int cleanLogsBefore(LocalDateTime dateTime) {
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(OperationLogEntity::getOperTime, dateTime);
        return baseMapper.delete(wrapper);
    }

    @Override
    public List<OperationLogEntity> pageQuery(int pageNum, int pageSize,
                                               String keyword, Map<String, Object> conditions) {
        Page<OperationLogEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<>();

        // 条件查询
        if (conditions != null) {
            Object statusObj = conditions.get("status");
            if (statusObj != null && !"".equals(statusObj.toString())) {
                wrapper.eq(OperationLogEntity::getStatus, Integer.valueOf(statusObj.toString()));
            }
            Object businessTypeObj = conditions.get("businessType");
            if (businessTypeObj != null && !"".equals(businessTypeObj.toString())) {
                wrapper.eq(OperationLogEntity::getBusinessType, businessTypeObj.toString());
            }
            Object startTime = conditions.get("startTime");
            if (startTime instanceof LocalDateTime st) {
                wrapper.ge(OperationLogEntity::getOperTime, st);
            }
            Object endTime = conditions.get("endTime");
            if (endTime instanceof LocalDateTime et) {
                wrapper.le(OperationLogEntity::getOperTime, et);
            }
        }

        // 关键词模糊查询
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(OperationLogEntity::getTitle, keyword)
                    .or().like(OperationLogEntity::getOperatorName, keyword)
                    .or().like(OperationLogEntity::getOperUrl, keyword));
        }

        wrapper.orderByDesc(OperationLogEntity::getOperTime);
        return baseMapper.selectPage(page, wrapper).getRecords();
    }
}
