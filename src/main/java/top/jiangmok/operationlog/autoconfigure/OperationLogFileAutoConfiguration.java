package top.jiangmok.operationlog.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.service.OperationLogService;
import top.jiangmok.operationlog.service.impl.OperationLogFileServiceImpl;

/**
 * 文件存储自动配置（默认）
 * 当 save-location=file（或不配置，默认值）时激活。
 * 零外部依赖——纯 JDK + Jackson，无需数据源、无需中间件。
 *
 * @author mok
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mok.operation-log.save-location", havingValue = "file", matchIfMissing = true)
@EnableConfigurationProperties(OperationLogProperties.class)
public class OperationLogFileAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OperationLogFileAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OperationLogService operationLogService(OperationLogProperties properties) {
        log.info("============= mok-operation-log-spring-boot-starter >> 启用 File 保存策略");
        return new OperationLogFileServiceImpl(properties);
    }
}
