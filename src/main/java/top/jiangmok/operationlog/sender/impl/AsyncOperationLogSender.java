package top.jiangmok.operationlog.sender.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.message.OperationLogMessage;
import top.jiangmok.operationlog.sender.OperationLogAsyncSender;
import top.jiangmok.operationlog.service.OperationLogService;

import java.time.LocalDateTime;

/**
 * 默认异步发送器（基于 Spring @Async）
 * <p>
 * 通过线程池异步将消息转换为实体后直接持久化，零外部中间件依赖。
 * 包含幂等检查：已成功的日志跳过，失败的重试。
 * </p>
 *
 * @author mok
 */
public class AsyncOperationLogSender implements OperationLogAsyncSender {

    private static final Logger log = LoggerFactory.getLogger(AsyncOperationLogSender.class);

    private final OperationLogService operationLogService;
    private final OperationLogProperties properties;

    public AsyncOperationLogSender(OperationLogService operationLogService,
                                   OperationLogProperties properties) {
        this.operationLogService = operationLogService;
        this.properties = properties;
    }

    @Override
    @Async("operationLogTaskExecutor")
    public void send(OperationLogMessage message) {
        try {
            OperationLogEntity entity = convertToEntity(message);

            // 幂等检查
            if (operationLogService.checkExistsById(entity.getId())) {
                log.debug("操作日志已存在，跳过: {}", message.getId());
                return;
            }

            // 保存
            operationLogService.saveOperationLog(entity);
            log.debug("操作日志已异步保存: {}", message.getTitle());

        } catch (Exception e) {
            log.error("异步保存操作日志失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 将消息 DTO 转换为实体
     */
    private OperationLogEntity convertToEntity(OperationLogMessage message) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(message.getId());
        entity.setTitle(message.getTitle());
        entity.setBusinessType(message.getBusinessType());
        entity.setMethod(message.getMethod());
        entity.setRequestMethod(message.getRequestMethod());
        entity.setOperUrl(message.getOperUrl());
        entity.setOperIp(message.getOperIp());
        entity.setOperatorId(message.getOperatorId());
        entity.setOperatorName(message.getOperatorName());
        entity.setOperatorType(message.getOperatorType());
        entity.setDeptName(message.getDeptName());
        entity.setOperParam(message.getOperParam());
        entity.setJsonResult(message.getJsonResult());
        entity.setStatus(message.getStatus());
        entity.setErrorMsg(message.getErrorMsg());
        entity.setCreateTime(LocalDateTime.now());
        entity.setOperTime(message.getOperTime() != null ? message.getOperTime() : LocalDateTime.now());
        return entity;
    }
}
