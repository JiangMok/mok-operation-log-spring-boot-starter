package top.jiangmok.operationlog.operator.impl;

import cn.dev33.satoken.stp.StpUtil;
import top.jiangmok.operationlog.operator.OperatorResolver;

/**
 * 基于 Sa-Token 的操作人解析器
 * <p>
 * 仅在 classpath 中存在 Sa-Token 时由 AutoConfiguration 自动注册。
 * 从 StpUtil 获取当前登录用户信息。
 * </p>
 *
 * @author mok
 */
public class SaTokenOperatorResolver implements OperatorResolver {

    @Override
    public String getOperatorId() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsString();
            }
        } catch (Exception ignored) {
        }
        return "UNKNOWN";
    }

    @Override
    public String getOperatorName() {
        try {
            if (StpUtil.isLogin()) {
                Object loginId = StpUtil.getLoginId();
                return loginId != null ? loginId.toString() : "UNKNOWN";
            }
        } catch (Exception ignored) {
        }
        return "UNKNOWN";
    }

    @Override
    public String getOperatorType() {
        return "ADMIN";
    }

    @Override
    public String getDeptName() {
        return null;
    }
}
