package top.jiangmok.operationlog.autoconfigure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.service.OperationLogService;
import top.jiangmok.operationlog.service.impl.OperationLogMySqlServiceImpl;

/**
 * MySQL 存储自动配置
 * <p>
 * 当 save-location=mysql 且 classpath 上存在 MyBatis-Plus 时激活。
 * 需要用户自行配置数据源。
 * </p>
 *
 * @author mok
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mok.operation-log.save-location", havingValue = "mysql")
@ConditionalOnClass({BaseMapper.class})
@EnableConfigurationProperties(OperationLogProperties.class)
@MapperScan("top.jiangmok.operationlog.mapper")
public class OperationLogMySqlAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OperationLogMySqlAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OperationLogService operationLogService(OperationLogProperties properties) {
        log.info("============= mok-operation-log-spring-boot-starter >> 启用 MySQL 保存策略");
        return new OperationLogMySqlServiceImpl(properties);
    }
}
