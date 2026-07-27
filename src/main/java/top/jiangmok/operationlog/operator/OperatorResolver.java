package top.jiangmok.operationlog.operator;

/**
 * 操作人解析器 SPI 接口
 * <p>
 * 接入方需要实现此接口并注册为 Spring Bean，以提供当前操作人的信息。
 * 如果不实现，默认返回 "SYSTEM"。
 * </p>
 *
 * @author mok
 */
public interface OperatorResolver {

    /** 获取操作人 ID */
    String getOperatorId();

    /** 获取操作人名称 */
    String getOperatorName();

    /** 获取操作人类型（如：ADMIN、USER 等） */
    String getOperatorType();

    /** 获取部门名称（可选，返回 null 亦可） */
    default String getDeptName() {
        return null;
    }
}
