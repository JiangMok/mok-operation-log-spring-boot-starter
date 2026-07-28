package top.jiangmok.operationlog.sender.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import top.jiangmok.operationlog.message.OperationLogMessage;
import top.jiangmok.operationlog.sender.OperationLogAsyncSender;

import static top.jiangmok.operationlog.constant.OperationLogMQConstant.OPERATION_LOG_EXCHANGE;
import static top.jiangmok.operationlog.constant.OperationLogMQConstant.OPERATION_LOG_ROUTING_KEY;

/**
 * RabbitMQ 异步发送器
 * <p>
 * 将操作日志消息投递到 RabbitMQ，
 * 由 {@link top.jiangmok.operationlog.consumer.OperationLogConsumer} 消费。
 * 完整保留重试/DLX/幂等逻辑。
 * </p>
 *
 * @author mok
 */
public class RabbitMQAsyncSender implements OperationLogAsyncSender {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQAsyncSender.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQAsyncSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(OperationLogMessage message) {
        rabbitTemplate.convertAndSend(OPERATION_LOG_EXCHANGE, OPERATION_LOG_ROUTING_KEY, message);
        log.debug("操作日志已发送到消息队列: {}", message.getTitle());
    }
}
