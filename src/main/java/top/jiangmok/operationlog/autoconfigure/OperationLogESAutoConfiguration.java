package top.jiangmok.operationlog.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import top.jiangmok.operationlog.repository.OperationLogRepository;
import top.jiangmok.operationlog.service.OperationLogService;
import top.jiangmok.operationlog.service.impl.OperationLogESServiceImpl;

/**
 * Elasticsearch 存储自动配置
 * <p>
 * 当 save-location=es 且 classpath 上存在 Elasticsearch 时激活。
 * </p>
 *
 * @author mok
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mok.operation-log.save-location", havingValue = "es")
@ConditionalOnClass(ElasticsearchRepository.class)
@EnableElasticsearchRepositories(basePackages = "top.jiangmok.operationlog.repository")
public class OperationLogESAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OperationLogESAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OperationLogService operationLogService(OperationLogRepository repository,
                                                   ElasticsearchOperations elasticsearchOperations) {
        log.info("============= mok-operation-log-spring-boot-starter >> 启用 ES 保存策略");
        return new OperationLogESServiceImpl(repository, elasticsearchOperations);
    }
}
