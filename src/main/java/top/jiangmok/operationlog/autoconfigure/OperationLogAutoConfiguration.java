package top.jiangmok.operationlog.autoconfigure;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetails;
import top.jiangmok.operationlog.aspect.OperationLogAspect;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.consumer.OperationLogConsumer;
import top.jiangmok.operationlog.desensitize.ParamDesensitizer;
import top.jiangmok.operationlog.desensitize.impl.DefaultParamDesensitizer;
import top.jiangmok.operationlog.operator.OperatorResolver;
import top.jiangmok.operationlog.operator.impl.DefaultOperatorResolver;
import top.jiangmok.operationlog.operator.impl.SaTokenOperatorResolver;
import top.jiangmok.operationlog.operator.impl.SecurityContextOperatorResolver;
import top.jiangmok.operationlog.service.OperationLogService;

import static top.jiangmok.operationlog.constant.OperationLogMQConstant.*;

/**
 * 操作日志自动配置
 * <p>
 * 声明 MQ 队列/交换机/绑定，注册切面、消费者、SPI 默认实现。
 * </p>
 *
 * @author mok
 */
@AutoConfiguration
@EnableConfigurationProperties(OperationLogProperties.class)
@ConditionalOnProperty(prefix = "mok.operation-log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OperationLogAutoConfiguration {

    // ==================== MQ 声明 ====================

    /** 操作日志交换机 */
    @Bean
    public Exchange operationLogExchange() {
        return ExchangeBuilder.topicExchange(OPERATION_LOG_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 操作日志队列 */
    @Bean
    public Queue operationLogQueue() {
        return QueueBuilder.durable(OPERATION_LOG_QUEUE)
                .deadLetterExchange(OPERATION_LOG_DLX_EXCHANGE)
                .deadLetterRoutingKey(OPERATION_LOG_DLX_ROUTING_KEY)
                .build();
    }

    /** 操作日志绑定 */
    @Bean
    public Binding operationLogBinding() {
        return BindingBuilder.bind(operationLogQueue())
                .to(operationLogExchange())
                .with(OPERATION_LOG_ROUTING_KEY)
                .noargs();
    }

    /** 死信交换机 */
    @Bean
    public Exchange operationLogDlxExchange() {
        return ExchangeBuilder.topicExchange(OPERATION_LOG_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 死信队列 */
    @Bean
    public Queue operationLogDlxQueue() {
        return QueueBuilder.durable(OPERATION_LOG_DLX_QUEUE).build();
    }

    /** 死信绑定 */
    @Bean
    public Binding operationLogDlxBinding() {
        return BindingBuilder.bind(operationLogDlxQueue())
                .to(operationLogDlxExchange())
                .with(OPERATION_LOG_DLX_ROUTING_KEY)
                .noargs();
    }

    // ==================== SPI 默认实现 ====================

    @Bean
    @ConditionalOnMissingBean
    public ParamDesensitizer paramDesensitizer() {
        return new DefaultParamDesensitizer();
    }

    /**
     * 操作人解析器
     * <p>优先级：用户自定义 > Sa-Token > Spring Security > 默认</p>
     */
    @Bean
    @ConditionalOnMissingBean(OperatorResolver.class)
    @ConditionalOnClass(StpUtil.class)
    public OperatorResolver saTokenOperatorResolver() {
        return new SaTokenOperatorResolver();
    }

    @Bean
    @ConditionalOnMissingBean(OperatorResolver.class)
    @ConditionalOnClass(UserDetails.class)
    public OperatorResolver securityContextOperatorResolver() {
        return new SecurityContextOperatorResolver();
    }

    @Bean
    @ConditionalOnMissingBean(OperatorResolver.class)
    public OperatorResolver defaultOperatorResolver() {
        return new DefaultOperatorResolver();
    }

    // ==================== 核心 Bean ====================

    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(
            OperationLogProperties properties,
            RabbitTemplate rabbitTemplate,
            OperatorResolver operatorResolver,
            ParamDesensitizer paramDesensitizer) {
        return new OperationLogAspect(properties, rabbitTemplate, operatorResolver, paramDesensitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationLogConsumer operationLogConsumer(OperationLogService operationLogService) {
        return new OperationLogConsumer(operationLogService);
    }
}
