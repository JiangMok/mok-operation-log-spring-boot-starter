package top.jiangmok.operationlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.jiangmok.operationlog.entity.OperationLogEntity;

/**
 * 操作日志 MyBatis-Plus Mapper
 *
 * @author mok
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
