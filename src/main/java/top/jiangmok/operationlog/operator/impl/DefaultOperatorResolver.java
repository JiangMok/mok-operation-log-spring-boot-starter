package top.jiangmok.operationlog.operator.impl;

import top.jiangmok.operationlog.operator.OperatorResolver;

/**
 * 默认操作人解析器
 * <p>
 * 当用户未提供自定义 OperatorResolver 实现时使用，所有字段返回默认值。
 * 生产环境建议用户自行实现并注册为 Spring Bean 来覆盖。
 * </p>
 *
 * @author mok
 */
public class DefaultOperatorResolver implements OperatorResolver {

    @Override
    public String getOperatorId() {
        return "SYSTEM";
    }

    @Override
    public String getOperatorName() {
        return "系统";
    }

    @Override
    public String getOperatorType() {
        return "SYSTEM";
    }

    @Override
    public String getDeptName() {
        return null;
    }
}
