package top.jiangmok.operationlog.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.model.OperationLogPageResult;
import top.jiangmok.operationlog.repository.OperationLogRepository;
import top.jiangmok.operationlog.service.OperationLogService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 Elasticsearch 存储实现
 *
 * @author mok
 */
public class OperationLogESServiceImpl implements OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogESServiceImpl.class);

    private final OperationLogRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;

    public OperationLogESServiceImpl(OperationLogRepository repository,
                                     ElasticsearchOperations elasticsearchOperations) {
        this.repository = repository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public void saveOperationLog(OperationLogEntity entity) {
        repository.save(entity);
    }

    @Override
    public OperationLogEntity findById(String id) {
        return repository.findById(id).orElse(null);
    }

    @Override
    public boolean checkExistsById(String id) {
        return repository.findById(id).isPresent();
    }

    @Override
    public int cleanLogsBefore(LocalDateTime dateTime) {
        if (dateTime == null) {
            return 0;
        }
        String formatted = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String jsonQuery = "{\"range\":{\"operTime\":{\"lt\":\"" + formatted + "\"}}}";

        StringQuery stringQuery = new StringQuery(jsonQuery);
        DeleteQuery deleteQuery = DeleteQuery.builder(stringQuery)
                .withRefresh(true)
                .build();

        ByQueryResponse response = elasticsearchOperations.delete(deleteQuery, OperationLogEntity.class);
        return (int) response.getDeleted();
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public List<OperationLogEntity> pageQuery(int pageNum, int pageSize,
                                               String keyword, Map<String, Object> conditions) {
        return pageQueryResult(pageNum, pageSize, keyword, conditions).getRecords();
    }

    @Override
    public OperationLogPageResult pageQueryResult(int pageNum, int pageSize,
                                                   String keyword, Map<String, Object> conditions) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 条件查询
        if (conditions != null) {
            Object statusObj = conditions.get("status");
            if (statusObj != null && !"".equals(statusObj.toString())) {
                int status = Integer.parseInt(statusObj.toString());
                boolBuilder.filter(q -> q.term(t -> t.field("status").value(status)));
            }
            Object businessTypeObj = conditions.get("businessType");
            if (businessTypeObj != null && !"".equals(businessTypeObj.toString())) {
                boolBuilder.filter(q -> q.term(t -> t.field("businessType").value(businessTypeObj.toString())));
            }
        }

        // 关键词模糊查询
        if (keyword != null && !keyword.isBlank()) {
            Query titleQuery = Query.of(q -> q.wildcard(w -> w.field("title").value("*" + keyword + "*")));
            Query operatorQuery = Query.of(q -> q.match(m -> m.field("operatorName").query(keyword)));
            Query urlQuery = Query.of(q -> q.wildcard(w -> w.field("operUrl").value("*" + keyword + "*")));

            boolBuilder.must(q -> q.bool(b -> b
                    .should(titleQuery)
                    .should(operatorQuery)
                    .should(urlQuery)
                    .minimumShouldMatch("1")));
        }

        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "operTime"));

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(boolBuilder.build()._toQuery())
                .withPageable(pageable)
                .build();

        SearchHits<OperationLogEntity> searchHits =
                elasticsearchOperations.search(searchQuery, OperationLogEntity.class);

        List<OperationLogEntity> records = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        return new OperationLogPageResult(records, searchHits.getTotalHits(), pageNum, pageSize);
    }
}
