package top.jiangmok.operationlog.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.entity.OperationLogEntity;
import top.jiangmok.operationlog.mapper.OperationLogMapper;
import top.jiangmok.operationlog.model.OperationLogPageResult;
import top.jiangmok.operationlog.service.impl.OperationLogFileServiceImpl;
import top.jiangmok.operationlog.service.impl.OperationLogMySqlServiceImpl;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationLogServiceEnhancementTest {

    @TempDir
    Path tempDir;

    @Test
    void fileStorageShouldReturnAccuratePageTotal() {
        OperationLogProperties properties = new OperationLogProperties();
        properties.getFile().setLogDir(tempDir.toString());
        properties.getFile().setMaxRetentionDays(0);
        OperationLogFileServiceImpl service = new OperationLogFileServiceImpl(properties);

        service.saveOperationLog(entity("1", "新增用户"));
        service.saveOperationLog(entity("2", "修改用户"));
        service.saveOperationLog(entity("3", "删除用户"));

        OperationLogPageResult result = service.pageQueryResult(2, 2, "用户", null);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getPageNum()).isEqualTo(2);
    }

    @Test
    void mysqlStorageShouldPropagatePersistenceException() {
        OperationLogProperties properties = new OperationLogProperties();
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        OperationLogMySqlServiceImpl service = new OperationLogMySqlServiceImpl(properties);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.insert(any(OperationLogEntity.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.saveOperationLog(entity("1", "新增用户")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mysqlStorageShouldReturnDatabaseTotal() {
        OperationLogProperties properties = new OperationLogProperties();
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        OperationLogMySqlServiceImpl service = new OperationLogMySqlServiceImpl(properties);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        Page<OperationLogEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(entity("1", "新增用户")));
        page.setTotal(42);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        OperationLogPageResult result = service.pageQueryResult(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(42);
        assertThat(result.getRecords()).hasSize(1);
    }

    private OperationLogEntity entity(String id, String title) {
        return new OperationLogEntity()
                .setId(id)
                .setTitle(title)
                .setStatus(0);
    }
}
