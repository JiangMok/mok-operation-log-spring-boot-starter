package top.jiangmok.operationlog.aspect;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.jiangmok.operationlog.annotation.OperationLog;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.constant.OperationLogMQConstant;
import top.jiangmok.operationlog.desensitize.ParamDesensitizer;
import top.jiangmok.operationlog.message.OperationLogMessage;
import top.jiangmok.operationlog.operator.OperatorResolver;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

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

    private final OperationLogProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final OperatorResolver operatorResolver;
    private final ParamDesensitizer paramDesensitizer;

    public OperationLogAspect(OperationLogProperties properties,
                              RabbitTemplate rabbitTemplate,
                              OperatorResolver operatorResolver,
                              ParamDesensitizer paramDesensitizer) {
        this.properties = properties;
        this.rabbitTemplate = rabbitTemplate;
        this.operatorResolver = operatorResolver;
        this.paramDesensitizer = paramDesensitizer;
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
        try {
            // 1. 获取当前请求
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();

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
            rabbitTemplate.convertAndSend(
                    OperationLogMQConstant.OPERATION_LOG_EXCHANGE,
                    OperationLogMQConstant.OPERATION_LOG_ROUTING_KEY,
                    message);
            log.debug("操作日志已发送到消息队列: {}", message.getTitle());

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
        message.setId(IdUtil.simpleUUID());
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

        // 状态
        if (e != null) {
            message.setStatus(1);
            message.setErrorMsg(StrUtil.sub(e.getMessage(), 0, 2000));
        } else {
            message.setStatus(0);
        }

        return message;
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
                    String jsonStr = JSONUtil.toJsonStr(args[i]);
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
