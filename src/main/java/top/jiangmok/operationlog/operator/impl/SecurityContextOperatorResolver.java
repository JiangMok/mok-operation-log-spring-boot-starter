package top.jiangmok.operationlog.operator.impl;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import top.jiangmok.operationlog.operator.OperatorResolver;

/**
 * 基于 Spring Security 的操作人解析器
 * <p>
 * 仅在 classpath 中存在 Spring Security 时由 AutoConfiguration 自动注册。
 * 从 SecurityContextHolder 获取当前登录用户信息。
 * </p>
 *
 * @author mok
 */
public class SecurityContextOperatorResolver implements OperatorResolver {

    @Override
    public String getOperatorId() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "UNKNOWN";
    }

    @Override
    public String getOperatorName() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername();
            }
            return authentication.getName();
        }
        return "UNKNOWN";
    }

    @Override
    public String getOperatorType() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(Object::toString)
                    .orElse("USER");
        }
        return "USER";
    }

    @Override
    public String getDeptName() {
        return null;
    }

    private Authentication getAuthentication() {
        try {
            return SecurityContextHolder.getContext().getAuthentication();
        } catch (Exception e) {
            return null;
        }
    }
}
