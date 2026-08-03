package top.jiangmok.operationlog.sender;

import top.jiangmok.operationlog.message.OperationLogMessage;

/**
 * 操作日志异步发送器 SPI 接口
 * 负责将日志消息异步发送到目标：
 * <ul>
 *   <li>{@code async} — 通过 @Async 线程池直接持久化</li>
 *   <li>{@code rabbitmq} — 投递到 RabbitMQ，由 Consumer 消费</li>
 * </ul>
 *
 * @author mok
 */
public interface OperationLogAsyncSender {

    /**
     * 异步发送操作日志消息
     *
     * @param message 操作日志消息
     */
    void send(OperationLogMessage message);
}
