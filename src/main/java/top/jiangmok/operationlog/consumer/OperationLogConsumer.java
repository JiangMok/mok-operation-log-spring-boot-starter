package top.jiangmok.operationlog.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.message.OperationLogMessage;
import top.jiangmok.operationlog.service.OperationLogService;

import java.io.IOException;
import java.time.LocalDateTime;

import static top.jiangmok.operationlog.constant.OperationLogMQConstant.*;

/**
 * 操作日志 MQ 消费者
 * <p>
 * 监听操作日志队列，完成持久化。包含幂等检查、重试机制和死信处理。
 * </p>
 *
 * @author mok
 */
public class OperationLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(OperationLogConsumer.class);

    private final OperationLogService operationLogService;

    public OperationLogConsumer(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 监听主队列
     */
    @RabbitListener(queues = OPERATION_LOG_QUEUE)
    public void handleOperationLog(OperationLogMessage message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.debug("接收到操作日志消息: {}", message.getTitle());

        OperationLogEntity entity = convertToEntity(message);

        try {
            // 1. 幂等性检查
            OperationLogEntity existLog = operationLogService.findById(message.getId());

            // 2. 已成功处理，直接 ACK
            if (existLog != null && Integer.valueOf(0).equals(existLog.getStatus())) {
                channel.basicAck(deliveryTag, false);
                log.debug("操作日志已成功处理，跳过: {}", message.getId());
                return;
            }

            // 3. 检查重试上限
            int currentRetry = (existLog != null && existLog.getRetryCount() != null)
                    ? existLog.getRetryCount() : 0;
            if (existLog != null
                    && Integer.valueOf(1).equals(existLog.getStatus())
                    && currentRetry >= OPERATION_LOG_MAX_RETRY) {
                log.warn("消息 {} 重试次数已达上限 {}，丢弃", message.getId(), OPERATION_LOG_MAX_RETRY);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 4. 保存日志
            entity.setRetryCount(currentRetry);
            saveOrUpdate(entity);
            channel.basicAck(deliveryTag, false);
            log.debug("操作日志保存成功: {}", message.getTitle());

        } catch (Exception e) {
            log.error("处理操作日志失败: {}", e.getMessage(), e);
            int retryCount = (entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1;
            entity.setStatus(1);
            entity.setRetryCount(retryCount);
            saveOrUpdate(entity);

            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("拒绝消息失败", ex);
            }
        }
    }

    /**
     * 监听死信队列
     * <p>
     * 子类可覆盖此方法来自定义死信处理逻辑。
     * </p>
     */
    @RabbitListener(queues = OPERATION_LOG_DLX_QUEUE)
    public void handleDlxOperationLog(Message message, Channel channel,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.error("操作日志进入死信队列，消息体: {}", new String(message.getBody()));
        try {
            // 调用可覆盖的钩子方法
            onSaveFailed(message);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理死信消息失败", e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("拒绝死信消息失败", ex);
            }
        }
    }

    /**
     * 死信保存失败时的钩子方法
     * <p>
     * 用户可覆盖此方法实现自定义的失败处理逻辑（如告警、落库等）。
     * 默认仅记录错误日志。
     * </p>
     *
     * @param message 死信消息
     */
    protected void onSaveFailed(Message message) {
        log.error("操作日志消息经 {} 次重试后仍失败，消息体: {}",
                OPERATION_LOG_MAX_RETRY, new String(message.getBody()));
    }

    /**
     * 保存或更新（根据是否存在判断）
     */
    private void saveOrUpdate(OperationLogEntity entity) {
        OperationLogEntity existLog = operationLogService.findById(entity.getId());
        if (existLog == null) {
            operationLogService.saveOperationLog(entity);
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
        entity.setOperatorName(message.getOperatorName());
        entity.setOperatorType(message.getOperatorType());
        entity.setOperParam(message.getOperParam());
        entity.setJsonResult(message.getJsonResult());
        entity.setStatus(message.getStatus());
        entity.setErrorMsg(message.getErrorMsg());
        entity.setCreateTime(LocalDateTime.now());
        entity.setOperTime(message.getOperTime() != null ? message.getOperTime() : LocalDateTime.now());
        return entity;
    }
}
