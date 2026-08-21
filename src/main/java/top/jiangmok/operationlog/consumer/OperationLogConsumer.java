package top.jiangmok.operationlog.consumer;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;

    public OperationLogConsumer(OperationLogService operationLogService) {
        this(operationLogService, null);
    }

    public OperationLogConsumer(OperationLogService operationLogService,
                                RabbitTemplate rabbitTemplate) {
        this.operationLogService = operationLogService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 监听主队列
     */
    @RabbitListener(queues = OPERATION_LOG_QUEUE)
    public void handleOperationLog(OperationLogMessage message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                   @Header(name = OPERATION_LOG_RETRY_HEADER, required = false)
                                   Integer retryCount) {
        log.debug("接收到操作日志消息: {}", message.getTitle());

        OperationLogEntity entity = convertToEntity(message);
        int currentRetry = retryCount == null ? 0 : retryCount;

        try {
            // 1. 幂等性检查
            if (operationLogService.checkExistsById(message.getId())) {
                channel.basicAck(deliveryTag, false);
                log.debug("操作日志已处理，跳过重复消息: {}", message.getId());
                return;
            }

            // 2. 保存日志，存储异常必须继续向外抛出，才能触发重试。
            entity.setRetryCount(currentRetry);
            operationLogService.saveOperationLog(entity);
            channel.basicAck(deliveryTag, false);
            log.debug("操作日志保存成功: {}", message.getTitle());

        } catch (Exception e) {
            log.error("处理操作日志失败: {}", e.getMessage(), e);
            retryOrSendToDeadLetter(message, channel, deliveryTag, currentRetry);
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

    private void retryOrSendToDeadLetter(OperationLogMessage message,
                                         Channel channel,
                                         long deliveryTag,
                                         int currentRetry) {
        if (rabbitTemplate != null && currentRetry < OPERATION_LOG_MAX_RETRY) {
            try {
                int nextRetry = currentRetry + 1;
                rabbitTemplate.convertAndSend(
                        OPERATION_LOG_EXCHANGE,
                        OPERATION_LOG_ROUTING_KEY,
                        message,
                        rabbitMessage -> {
                            rabbitMessage.getMessageProperties()
                                    .setHeader(OPERATION_LOG_RETRY_HEADER, nextRetry);
                            return rabbitMessage;
                        });
                channel.basicAck(deliveryTag, false);
                log.warn("操作日志消息已重新投递，第 {} 次重试: {}", nextRetry, message.getId());
                return;
            } catch (Exception retryException) {
                log.error("重新投递操作日志消息失败，将原消息重新入队", retryException);
                reject(channel, deliveryTag, true);
                return;
            }
        }

        log.error("操作日志消息达到最大重试次数，进入死信队列: {}", message.getId());
        reject(channel, deliveryTag, false);
    }

    private void reject(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (IOException exception) {
            log.error("拒绝操作日志消息失败", exception);
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
