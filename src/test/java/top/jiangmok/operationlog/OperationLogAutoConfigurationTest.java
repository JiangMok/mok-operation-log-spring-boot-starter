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
import top.jiangmok.operationlog.service.OperationLogService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 自动装配核心测试
 * <p>
 * 只测试主配置 OperationLogAutoConfiguration（不含存储后端），
 * 存储后端（MySQL/ES）需要真实数据库，通过集成测试覆盖。
 * </p>
 */
@DisplayName("操作日志自动装配")
class OperationLogAutoConfigurationTest {

    /** 基础 Runner：只加载主配置，mock 外部依赖 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OperationLogAutoConfiguration.class
            ))
            // 排除 RabbitMQ 和数据源自动配置
            .withPropertyValues(
                    "spring.autoconfigure.exclude=" +
                            "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration," +
                            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
            );

    /** 提供 mock 依赖，让容器能正常启动 */
    private ApplicationContextRunner withMocks() {
        return contextRunner
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(OperationLogService.class, () -> mock(OperationLogService.class));
    }

    // ==================== 测试用例 ====================

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
                        // MQ 基础设施也不应该有
                        assertThat(context).doesNotHaveBean("operationLogQueue");
                    });
        }

        @Test
        @DisplayName("enabled=true（默认）→ 所有核心 Bean 创建")
        void shouldCreateAllCoreBeansWhenEnabled() {
            withMocks()
                    .run(context -> {
                        assertThat(context).hasSingleBean(OperationLogProperties.class);
                        assertThat(context).hasSingleBean(ParamDesensitizer.class);
                        assertThat(context).hasSingleBean(OperatorResolver.class);
                        assertThat(context).hasSingleBean(OperationLogAspect.class);
                        assertThat(context).hasSingleBean(OperationLogConsumer.class);
                        // MQ 基础设施
                        assertThat(context).hasBean("operationLogQueue");
                        assertThat(context).hasBean("operationLogExchange");
                        assertThat(context).hasBean("operationLogDlxQueue");
                        assertThat(context).hasBean("operationLogDlxExchange");
                    });
        }
    }

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
                        assertThat(resolver).isNotNull();
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
    }

    @Nested
    @DisplayName("默认实现")
    class DefaultImplementations {

        @Test
        @DisplayName("无用户 OperatorResolver → 自动选择（SaToken 可用时优先）")
        void shouldAutoSelectOperatorResolver() {
            withMocks()
                    .run(context -> {
                        OperatorResolver resolver = context.getBean(OperatorResolver.class);
                        // 测试环境有 Sa-Token，所以是 SaTokenOperatorResolver
                        assertThat(resolver).isNotNull();
                        assertThat(resolver.getOperatorId()).isNotNull();
                        assertThat(resolver.getOperatorType()).isNotNull();
                    });
        }

        @Test
        @DisplayName("无用户 ParamDesensitizer → DefaultParamDesensitizer（过滤敏感字段）")
        void shouldFallbackToDefaultDesensitizer() {
            withMocks()
                    .run(context -> {
                        ParamDesensitizer d = context.getBean(ParamDesensitizer.class);
                        assertThat(d).isInstanceOf(DefaultParamDesensitizer.class);
                        String result = d.desensitize("{\"password\":\"123456\",\"name\":\"mok\"}");
                        assertThat(result).doesNotContain("123456");  // password 被过滤
                        assertThat(result).contains("mok");            // name 保留
                    });
        }
    }

    @Nested
    @DisplayName("配置属性绑定")
    class PropertiesBinding {

        @Test
        @DisplayName("自定义属性全部注入")
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
                    });
        }

        @Test
        @DisplayName("不配置 → 使用全部默认值")
        void shouldUseAllDefaults() {
            withMocks()
                    .run(context -> {
                        OperationLogProperties props = context.getBean(OperationLogProperties.class);
                        assertThat(props.getEnabled()).isTrue();
                        assertThat(props.getSaveLocation()).isEqualTo("mysql");
                        assertThat(props.getRecordGet()).isTrue();
                        assertThat(props.getMaxContentLength()).isEqualTo(2000);
                    });
        }
    }
}
