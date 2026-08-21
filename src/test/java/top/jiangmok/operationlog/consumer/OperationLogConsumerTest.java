package top.jiangmok.operationlog.consumer;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.message.OperationLogMessage;
import top.jiangmok.operationlog.service.OperationLogService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static top.jiangmok.operationlog.constant.OperationLogMQConstant.OPERATION_LOG_EXCHANGE;
import static top.jiangmok.operationlog.constant.OperationLogMQConstant.OPERATION_LOG_MAX_RETRY;
import static top.jiangmok.operationlog.constant.OperationLogMQConstant.OPERATION_LOG_ROUTING_KEY;

class OperationLogConsumerTest {

    @Test
    void shouldPersistAllOperatorFieldsAndAck() throws Exception {
        OperationLogService service = mock(OperationLogService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        OperationLogConsumer consumer = new OperationLogConsumer(service, rabbitTemplate);
        OperationLogMessage message = message();

        consumer.handleOperationLog(message, channel, 1L, null);

        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(service).saveOperationLog(captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo("42");
        assertThat(captor.getValue().getOperatorName()).isEqualTo("测试用户");
        assertThat(captor.getValue().getDeptName()).isEqualTo("技术部");
        verify(channel).basicAck(1L, false);
    }

    @Test
    void shouldRepublishWithRetryHeaderWhenPersistenceFails() throws Exception {
        OperationLogService service = mock(OperationLogService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        OperationLogConsumer consumer = new OperationLogConsumer(service, rabbitTemplate);
        OperationLogMessage message = message();
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).saveOperationLog(any(OperationLogEntity.class));

        consumer.handleOperationLog(message, channel, 2L, 0);

        verify(rabbitTemplate).convertAndSend(
                eq(OPERATION_LOG_EXCHANGE),
                eq(OPERATION_LOG_ROUTING_KEY),
                eq(message),
                any(MessagePostProcessor.class));
        verify(channel).basicAck(2L, false);
        verify(channel, never()).basicNack(any(Long.class), eq(false), any(Boolean.class));
    }

    @Test
    void shouldSendToDeadLetterAfterMaximumRetries() throws Exception {
        OperationLogService service = mock(OperationLogService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        OperationLogConsumer consumer = new OperationLogConsumer(service, rabbitTemplate);
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).saveOperationLog(any(OperationLogEntity.class));

        consumer.handleOperationLog(message(), channel, 3L, OPERATION_LOG_MAX_RETRY);

        verify(channel).basicNack(3L, false, false);
        verify(rabbitTemplate, never()).convertAndSend(
                any(String.class), any(String.class), any(), any(MessagePostProcessor.class));
    }

    @Test
    void shouldAckDuplicateMessageWithoutSavingAgain() throws Exception {
        OperationLogService service = mock(OperationLogService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        OperationLogConsumer consumer = new OperationLogConsumer(service, rabbitTemplate);
        when(service.checkExistsById("log-1")).thenReturn(true);

        consumer.handleOperationLog(message(), channel, 4L, 1);

        verify(service, never()).saveOperationLog(any());
        verify(channel).basicAck(4L, false);
    }

    private OperationLogMessage message() {
        OperationLogMessage message = new OperationLogMessage();
        message.setId("log-1");
        message.setTitle("更新用户");
        message.setOperatorId("42");
        message.setOperatorName("测试用户");
        message.setOperatorType("ADMIN");
        message.setDeptName("技术部");
        message.setStatus(0);
        return message;
    }
}

