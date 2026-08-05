package top.jiangmok.operationlog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.jiangmok.operationlog.aspect.OperationLogAspect;
import top.jiangmok.operationlog.autoconfigure.OperationLogAutoConfiguration;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.consumer.OperationLogConsumer;
import top.jiangmok.operationlog.desensitize.ParamDesensitizer;
import top.jiangmok.operationlog.desensitize.impl.DefaultParamDesensitizer;
import top.jiangmok.operationlog.operator.OperatorResolver;
import top.jiangmok.operationlog.sender.OperationLogAsyncSender;
import top.jiangmok.operationlog.sender.impl.RabbitMQAsyncSender;
import top.jiangmok.operationlog.service.OperationLogService;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 自动装配核心测试
 * <p>
 * 覆盖 async（默认）和 rabbitmq 两种策略，以及 SPI 覆盖、属性绑定。
 * </p>
 */
@DisplayName("操作日志自动装配")
class OperationLogAutoConfigurationTest {

    /** 基础 Runner：加载主配置，排除数据源自动配置 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OperationLogAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.autoconfigure.exclude=" +
                            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
            );

    /** 提供 mock 依赖 */
    private ApplicationContextRunner withMocks() {
        return contextRunner
                .withBean(OperationLogService.class, () -> mock(OperationLogService.class));
    }

    /** 提供 rabbitmq 策略所需的 mock 依赖 */
    private ApplicationContextRunner withRabbitMocks() {
        return withMocks()
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class));
    }

    // ==================== 开关控制 ====================

    @Nested
    @DisplayName("开关控制")
    class Toggle {

        @Test
        @DisplayName("enabled=false → 全部核心 Bean 都不创建")
        void shouldSkipAllWhenDisabled() {
            contextRunner
                    .withPropertyValues("mok.operation-log.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(OperationLogAspect.class);
                        assertThat(context).doesNotHaveBean(OperationLogConsumer.class);
                        assertThat(context).doesNotHaveBean(ParamDesensitizer.class);
                        assertThat(context).doesNotHaveBean(OperatorResolver.class);
                        assertThat(context).doesNotHaveBean(OperationLogAsyncSender.class);
                        assertThat(context).doesNotHaveBean("operationLogTaskExecutor");
                    });
        }

        @Test
        @DisplayName("enabled=true（默认）→ 核心 Bean 全部创建")
        void shouldCreateAllCoreBeansWhenEnabled() {
            withMocks()
                    .run(context -> {
                        assertThat(context).hasSingleBean(OperationLogProperties.class);
                        assertThat(context).hasSingleBean(ParamDesensitizer.class);
                        assertThat(context).hasSingleBean(OperatorResolver.class);
                        assertThat(context).hasSingleBean(OperationLogAspect.class);
                        assertThat(context).hasSingleBean(OperationLogAsyncSender.class);
                        assertThat(context).hasBean("operationLogTaskExecutor");
                    });
        }
    }

    // ==================== 策略选择 ====================

    @Nested
    @DisplayName("策略选择")
    class Strategy {

        @Test
        @DisplayName("默认 async 策略 → AsyncOperationLogSender，无 MQ Bean")
        void shouldUseAsyncSenderByDefault() {
            withMocks()
                    .run(context -> {
                        // 发送器（@Async 代理，通过接口类型检查）
                        assertThat(context).hasSingleBean(OperationLogAsyncSender.class);
                        OperationLogAsyncSender sender = context.getBean(OperationLogAsyncSender.class);
                        assertThat(sender).isNotNull();

                        // 线程池
                        assertThat(context).hasBean("operationLogTaskExecutor");
                        assertThat(context.getBean("operationLogTaskExecutor"))
                                .isInstanceOf(Executor.class);

                        // 无 MQ 基础设施
                        assertThat(context).doesNotHaveBean("operationLogExchange");
                        assertThat(context).doesNotHaveBean("operationLogQueue");
                        assertThat(context).doesNotHaveBean("operationLogDlxExchange");
                        assertThat(context).doesNotHaveBean("operationLogDlxQueue");

                        // 无 Consumer
                        assertThat(context).doesNotHaveBean(OperationLogConsumer.class);

                        // 切面存在
                        assertThat(context).hasSingleBean(OperationLogAspect.class);
                    });
        }

        @Test
        @DisplayName("配置 rabbitmq 策略 → 验证发送器类型和 MQ Bean")
        void shouldRecognizeRabbitmqStrategy() {
            withRabbitMocks()
                    .withPropertyValues("mok.operation-log.async-strategy=rabbitmq")
                    .run(context -> {
                        // 策略属性正确绑定
                        OperationLogProperties props = context.getBean(OperationLogProperties.class);
                        assertThat(props.getAsyncStrategy()).isEqualTo("rabbitmq");

                        // 发送器
                        assertThat(context).hasSingleBean(OperationLogAsyncSender.class);
                        assertThat(context.getBean(OperationLogAsyncSender.class))
                                .isInstanceOf(RabbitMQAsyncSender.class);

                        // MQ 基础设施存在
                        assertThat(context).hasBean("operationLogExchange");
                        assertThat(context).hasBean("operationLogQueue");

                        // Consumer 存在
                        assertThat(context).hasSingleBean(OperationLogConsumer.class);
                    });
        }
    }

    // ==================== 线程池配置 ====================

    @Nested
    @DisplayName("线程池配置")
    class TaskExecutor {

        @Test
        @DisplayName("默认线程池配置")
        void shouldUseDefaultThreadPoolConfig() {
            withMocks()
                    .run(context -> {
                        OperationLogProperties props = context.getBean(OperationLogProperties.class);
                        assertThat(props.getTaskExecutor()).isNotNull();
                        assertThat(props.getTaskExecutor().getCoreSize()).isEqualTo(2);
                        assertThat(props.getTaskExecutor().getMaxSize()).isEqualTo(4);
                        assertThat(props.getTaskExecutor().getQueueCapacity()).isEqualTo(200);
                    });
        }

        @Test
        @DisplayName("自定义线程池配置")
        void shouldBindCustomThreadPoolConfig() {
            withMocks()
                    .withPropertyValues(
                            "mok.operation-log.task-executor.core-size=5",
                            "mok.operation-log.task-executor.max-size=10",
                            "mok.operation-log.task-executor.queue-capacity=500"
                    )
                    .run(context -> {
                        OperationLogProperties props = context.getBean(OperationLogProperties.class);
                        assertThat(props.getTaskExecutor().getCoreSize()).isEqualTo(5);
                        assertThat(props.getTaskExecutor().getMaxSize()).isEqualTo(10);
                        assertThat(props.getTaskExecutor().getQueueCapacity()).isEqualTo(500);
                    });
        }
    }

    // ==================== SPI 覆盖 ====================

    @Nested
    @DisplayName("SPI 覆盖（用户自定义优先）")
    class SpiOverride {

        @Test
        @DisplayName("用户注册 OperatorResolver → 用户的生效")
        void shouldPreferCustomOperatorResolver() {
            withMocks()
                    .withBean(OperatorResolver.class, () -> new OperatorResolver() {
                        @Override
                        public String getOperatorId() { return "custom-123"; }

                        @Override
                        public String getOperatorName() { return "自定义用户"; }

                        @Override
                        public String getOperatorType() { return "VIP"; }
                    })
                    .run(context -> {
                        OperatorResolver resolver = context.getBean(OperatorResolver.class);
                        assertThat(resolver.getOperatorId()).isEqualTo("custom-123");
                        assertThat(resolver.getOperatorType()).isEqualTo("VIP");
                    });
        }

        @Test
        @DisplayName("用户注册 ParamDesensitizer → 用户的生效")
        void shouldPreferCustomDesensitizer() {
            withMocks()
                    .withBean(ParamDesensitizer.class,
                            () -> (ParamDesensitizer) param -> "***脱敏***")
                    .run(context -> {
                        ParamDesensitizer d = context.getBean(ParamDesensitizer.class);
                        assertThat(d.desensitize("test")).isEqualTo("***脱敏***");
                    });
        }

        @Test
        @DisplayName("用户注册自定义 Sender → 用户的生效")
        void shouldPreferCustomSender() {
            withMocks()
                    .withBean(OperationLogAsyncSender.class,
                            () -> msg -> { /* no-op */ })
                    .run(context -> {
                        assertThat(context).hasSingleBean(OperationLogAsyncSender.class);
                    });
        }
    }

    @Nested
    @DisplayName("默认实现")
    class DefaultImplementations {

        @Test
        @DisplayName("无用户 OperatorResolver → 自动选择")
        void shouldAutoSelectOperatorResolver() {
            withMocks()
                    .run(context -> {
                        OperatorResolver resolver = context.getBean(OperatorResolver.class);
                        assertThat(resolver).isNotNull();
                        assertThat(resolver.getOperatorId()).isNotNull();
                        assertThat(resolver.getOperatorType()).isNotNull();
                    });
        }

        @Test
        @DisplayName("无用户 ParamDesensitizer → DefaultParamDesensitizer")
        void shouldFallbackToDefaultDesensitizer() {
            withMocks()
                    .run(context -> {
                        ParamDesensitizer d = context.getBean(ParamDesensitizer.class);
                        assertThat(d).isInstanceOf(DefaultParamDesensitizer.class);
                        String result = d.desensitize("{\"password\":\"123456\",\"name\":\"mok\"}");
                        assertThat(result).doesNotContain("123456");
                        assertThat(result).contains("mok");
                    });
        }

        @Test
        @DisplayName("默认 Sender 实现了 OperationLogAsyncSender 接口")
        void shouldDefaultToAsyncSender() {
            withMocks()
                    .run(context -> {
                        OperationLogAsyncSender sender = context.getBean(OperationLogAsyncSender.class);
                        assertThat(sender).isNotNull();
                        // @Async 代理，所以直接判接口
                        assertThat(sender).isInstanceOf(OperationLogAsyncSender.class);
                    });
        }
    }

    @Nested
    @DisplayName("配置属性绑定")
    class PropertiesBinding {

        @Test
        @DisplayName("自定义属性全部注入（async 默认策略）")
        void shouldBindCustomProperties() {
            withMocks()
                    .withPropertyValues(
                            "mok.operation-log.enabled=true",
                            "mok.operation-log.save-location=es",
                            "mok.operation-log.record-get=false",
                            "mok.operation-log.max-content-length=5000"
                    )
                    .run(context -> {
                        OperationLogProperties props = context.getBean(OperationLogProperties.class);
                        assertThat(props.getEnabled()).isTrue();
                        assertThat(props.getSaveLocation()).isEqualTo("es");
                        assertThat(props.getRecordGet()).isFalse();
                        assertThat(props.getMaxContentLength()).isEqualTo(5000);
                        assertThat(props.getAsyncStrategy()).isEqualTo("async");
                    });
        }

        @Test
        @DisplayName("不配置 → 使用全部默认值")
        void shouldUseAllDefaults() {
            withMocks()
                    .run(context -> {
                        OperationLogProperties props = context.getBean(OperationLogProperties.class);
                        assertThat(props.getEnabled()).isTrue();
                        assertThat(props.getSaveLocation()).isEqualTo("file");
                        assertThat(props.getRecordGet()).isTrue();
                        assertThat(props.getMaxContentLength()).isEqualTo(2000);
                        assertThat(props.getAsyncStrategy()).isEqualTo("async");
                    });
        }
    }
}
