package top.jiangmok.operationlog.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;
import top.jiangmok.operationlog.entity.OperationLogEntity;

/**
 * 操作日志 Elasticsearch Repository
 *
 * @author mok
 */
@Repository
public interface OperationLogRepository extends ElasticsearchRepository<OperationLogEntity, String> {
}
