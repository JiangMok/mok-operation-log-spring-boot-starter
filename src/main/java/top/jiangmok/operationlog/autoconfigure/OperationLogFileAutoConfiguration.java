package top.jiangmok.operationlog.autoconfigure;

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

    @Bean
    @ConditionalOnMissingBean
    public OperationLogService operationLogService(OperationLogProperties properties) {
        return new OperationLogFileServiceImpl(properties);
    }
}
