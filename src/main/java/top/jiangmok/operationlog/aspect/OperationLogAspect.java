package top.jiangmok.operationlog.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.jiangmok.operationlog.annotation.OperationLog;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.desensitize.ParamDesensitizer;
import top.jiangmok.operationlog.enums.OperationStatus;
import top.jiangmok.operationlog.message.OperationLogMessage;
import top.jiangmok.operationlog.operator.OperatorResolver;
import top.jiangmok.operationlog.sender.OperationLogAsyncSender;
import top.jiangmok.operationlog.util.IdGenerator;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 操作日志 AOP 切面
 * <p>
 * 拦截 @OperationLog 注解的方法，收集操作信息并通过 RabbitMQ 异步发送。
 * </p>
 *
 * @author mok
 */
@Aspect
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OperationLogProperties properties;
    private final OperationLogAsyncSender sender;
    private final OperatorResolver operatorResolver;
    private final ParamDesensitizer paramDesensitizer;

    /** 全局配置的业务异常类（启动时解析，解析失败的类名打 warn 并忽略） */
    private final List<Class<?>> configBusinessExceptions;

    public OperationLogAspect(OperationLogProperties properties,
                              OperationLogAsyncSender sender,
                              OperatorResolver operatorResolver,
                              ParamDesensitizer paramDesensitizer) {
        this.properties = properties;
        this.sender = sender;
        this.operatorResolver = operatorResolver;
        this.paramDesensitizer = paramDesensitizer;
        this.configBusinessExceptions = resolveBusinessExceptions(properties.getBusinessExceptions());
    }

    @Pointcut("@annotation(top.jiangmok.operationlog.annotation.OperationLog)")
    public void operationLogPointCut() {
    }

    @AfterReturning(pointcut = "operationLogPointCut()", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, Object jsonResult) {
        handleLog(joinPoint, null, jsonResult);
    }

    @AfterThrowing(pointcut = "operationLogPointCut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e, null);
    }

    /**
     * 收集日志信息并通过消息队列发送
     */
    protected void handleLog(final JoinPoint joinPoint, final Exception e, Object jsonResult) {
        // 1. 获取当前请求（Web 环境）
        HttpServletRequest request;
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            request = attributes.getRequest();
        } catch (NoClassDefFoundError ex) {
            // 非 Web 环境（classpath 未引入 spring-boot-starter-web），跳过日志记录
            log.debug("Non-web environment, skipping operation log");
            return;
        }

        try {
            // 2. 获取方法上的 @OperationLog 注解
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            OperationLog annotation = method.getAnnotation(OperationLog.class);

            // 3. 检查是否要记录 GET 请求
            if (!properties.getRecordGet() && "GET".equalsIgnoreCase(request.getMethod())) {
                return;
            }

            // 4. 构建并发送消息
            OperationLogMessage message = buildMessage(joinPoint, request, annotation, e, jsonResult);
            sender.send(message);
            log.debug("操作日志已发送: {}", message.getTitle());

        } catch (Exception ex) {
            log.error("构建操作日志消息失败", ex);
        }
    }

    /**
     * 构建操作日志消息
     */
    private OperationLogMessage buildMessage(JoinPoint joinPoint, HttpServletRequest request,
                                              OperationLog annotation, Exception e, Object jsonResult) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        OperationLogMessage message = new OperationLogMessage();

        // 基本信息
        message.setId(IdGenerator.generate());
        message.setTitle(annotation.title());
        message.setBusinessType(annotation.businessType().getValue());
        message.setMethod(method.getDeclaringClass().getName() + "." + method.getName());
        message.setRequestMethod(request.getMethod());
        message.setOperUrl(request.getRequestURI());
        message.setOperIp(getIpAddress(request));
        message.setOperTime(LocalDateTime.now());

        // 操作人信息（通过 SPI 接口获取，解耦认证框架）
        try {
            message.setOperatorName(operatorResolver.getOperatorName());
            message.setOperatorType(operatorResolver.getOperatorType());
        } catch (Exception ex) {
            log.warn("获取操作人信息失败", ex);
            message.setOperatorName("UNKNOWN");
            message.setOperatorType("UNKNOWN");
        }

        // 请求参数（脱敏后）
        if (annotation.saveRequestParam()) {
            message.setOperParam(buildOperParam(joinPoint));
        }

        // 响应数据
        if (annotation.saveResponseData() && jsonResult != null) {
            message.setJsonResult(jsonResult.toString());
        }

        // 状态：0-成功, 1-失败（系统异常）, 2-业务失败（业务校验未通过等非系统异常）
        if (e != null) {
            message.setStatus(isBusinessException(e, annotation)
                    ? OperationStatus.BIZ_FAIL.getValue() : OperationStatus.FAIL.getValue());
            String errMsg = e.getMessage();
            message.setErrorMsg(errMsg != null && errMsg.length() > 2000
                    ? errMsg.substring(0, 2000) : errMsg);
        } else {
            message.setStatus(OperationStatus.SUCCESS.getValue());
        }

        return message;
    }

    /**
     * 判断异常是否为"业务异常"（业务校验未通过等非系统异常）
     * <p>
     * 匹配规则：注解 businessExceptions 与全局配置 business-exceptions 取并集，子类自动命中。
     * </p>
     */
    private boolean isBusinessException(Throwable e, OperationLog annotation) {
        for (Class<? extends Throwable> clazz : annotation.businessExceptions()) {
            if (clazz.isAssignableFrom(e.getClass())) {
                return true;
            }
        }
        for (Class<?> clazz : configBusinessExceptions) {
            if (clazz.isAssignableFrom(e.getClass())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析全局配置的业务异常类名（启动时执行一次）
     * <p>类名写错（无法加载）时打 warn 并忽略该项，不影响其他配置和业务运行。</p>
     */
    private static List<Class<?>> resolveBusinessExceptions(List<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Class<?>> result = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            try {
                result.add(Class.forName(className.trim()));
            } catch (ClassNotFoundException e) {
                log.warn("业务异常类 {} 加载失败，已忽略该配置项", className, e);
            }
        }
        return result;
    }

    /**
     * 构建请求参数字符串（含脱敏）
     */
    private String buildOperParam(JoinPoint joinPoint) {
        try {
            StringBuilder sb = new StringBuilder();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    String jsonStr = MAPPER.writeValueAsString(args[i]);
                    String desensitized = paramDesensitizer.desensitize(jsonStr);
                    sb.append(desensitized);
                    if (i < args.length - 1) {
                        sb.append(",");
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建请求参数失败", e);
            return "";
        }
    }

    /**
     * 获取客户端 IP 地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个 IP 时取第一个
        if (ip != null && ip.length() > 15 && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(","));
        }
        return ip;
    }
}
