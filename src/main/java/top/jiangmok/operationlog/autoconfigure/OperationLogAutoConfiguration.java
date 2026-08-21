package top.jiangmok.operationlog.autoconfigure;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
import top.jiangmok.operationlog.sender.OperationLogAsyncSender;
import top.jiangmok.operationlog.sender.impl.AsyncOperationLogSender;
import top.jiangmok.operationlog.sender.impl.RabbitMQAsyncSender;
import top.jiangmok.operationlog.service.OperationLogService;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
@EnableAsync
@EnableConfigurationProperties(OperationLogProperties.class)
@ConditionalOnProperty(prefix = "mok.operation-log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OperationLogAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "mok.operation-log", name = "startup-print", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner operationLogStartupRunner(OperationLogProperties properties) {
        return args -> {
            log.info("============= mok-operation-log-spring-boot-starter >> 已启用");
            log.info("============= mok-operation-log-spring-boot-starter >> 异步策略: {}", properties.getAsyncStrategy());
        };
    }

    // ==================== 线程池 ====================

    /**
     * 操作日志专用线程池
     * <p>仅 async 策略生效，用户可通过 mok.operation-log.task-executor.* 配置。</p>
     */
    @Bean("operationLogTaskExecutor")
    @ConditionalOnMissingBean(name = "operationLogTaskExecutor")
    public Executor operationLogTaskExecutor(OperationLogProperties properties) {
        OperationLogProperties.TaskExecutorProperties config = properties.getTaskExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix("operation-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    // ==================== MQ 声明（仅 rabbitmq 策略） ====================

    /** 操作日志交换机 */
    @Bean
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public Exchange operationLogExchange() {
        return ExchangeBuilder.topicExchange(OPERATION_LOG_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 操作日志队列 */
    @Bean
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public Queue operationLogQueue() {
        return QueueBuilder.durable(OPERATION_LOG_QUEUE)
                .deadLetterExchange(OPERATION_LOG_DLX_EXCHANGE)
                .deadLetterRoutingKey(OPERATION_LOG_DLX_ROUTING_KEY)
                .build();
    }

    /** 操作日志绑定 */
    @Bean
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public Binding operationLogBinding() {
        return BindingBuilder.bind(operationLogQueue())
                .to(operationLogExchange())
                .with(OPERATION_LOG_ROUTING_KEY)
                .noargs();
    }

    /** 死信交换机 */
    @Bean
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public Exchange operationLogDlxExchange() {
        return ExchangeBuilder.topicExchange(OPERATION_LOG_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    /** 死信队列 */
    @Bean
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public Queue operationLogDlxQueue() {
        return QueueBuilder.durable(OPERATION_LOG_DLX_QUEUE).build();
    }

    /** 死信绑定 */
    @Bean
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public Binding operationLogDlxBinding() {
        return BindingBuilder.bind(operationLogDlxQueue())
                .to(operationLogDlxExchange())
                .with(OPERATION_LOG_DLX_ROUTING_KEY)
                .noargs();
    }

    // ==================== 发送器（策略选择） ====================

    /**
     * RabbitMQ 发送器
     * <p>当 async-strategy=rabbitmq 且 classpath 有 RabbitTemplate 时生效。</p>
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogAsyncSender.class)
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public OperationLogAsyncSender rabbitMQAsyncSender(RabbitTemplate rabbitTemplate) {
        return new RabbitMQAsyncSender(rabbitTemplate);
    }

    /**
     * 默认异步发送器
     * <p>使用 @Async 线程池，零外部中间件依赖。</p>
     */
    @Bean
    @ConditionalOnMissingBean(OperationLogAsyncSender.class)
    public OperationLogAsyncSender asyncOperationLogSender(
            OperationLogService operationLogService,
            OperationLogProperties properties) {
        return new AsyncOperationLogSender(operationLogService, properties);
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
            OperationLogAsyncSender sender,
            OperatorResolver operatorResolver,
            ParamDesensitizer paramDesensitizer) {
        return new OperationLogAspect(properties, sender, operatorResolver, paramDesensitizer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "mok.operation-log.async-strategy", havingValue = "rabbitmq")
    @ConditionalOnClass(RabbitTemplate.class)
    public OperationLogConsumer operationLogConsumer(
            OperationLogService operationLogService,
            RabbitTemplate rabbitTemplate) {
        return new OperationLogConsumer(operationLogService, rabbitTemplate);
    }
}
