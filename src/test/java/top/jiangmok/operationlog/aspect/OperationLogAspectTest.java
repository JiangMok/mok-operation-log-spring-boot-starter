package top.jiangmok.operationlog.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import top.jiangmok.operationlog.annotation.OperationLog;
import top.jiangmok.operationlog.config.OperationLogProperties;
import top.jiangmok.operationlog.desensitize.ParamDesensitizer;
import top.jiangmok.operationlog.enums.OperationStatus;
import top.jiangmok.operationlog.message.OperationLogMessage;
import top.jiangmok.operationlog.operator.OperatorResolver;
import top.jiangmok.operationlog.sender.OperationLogAsyncSender;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 切面异常分类测试
 * <p>
 * 验证业务异常（status=2）判定：注解与全局配置并集、子类匹配、类名解析容错、默认行为不变。
 * </p>
 */
@DisplayName("切面异常分类")
class OperationLogAspectTest {

    private OperationLogAsyncSender sender;
    private OperatorResolver operatorResolver;
    private ParamDesensitizer paramDesensitizer;

    @BeforeEach
    void setUp() {
        sender = mock(OperationLogAsyncSender.class);
        operatorResolver = mock(OperatorResolver.class);
        when(operatorResolver.getOperatorName()).thenReturn("测试用户");
        when(operatorResolver.getOperatorType()).thenReturn("ADMIN");
        paramDesensitizer = mock(ParamDesensitizer.class);
        when(paramDesensitizer.desensitize(any())).thenReturn("{}");
    }

    // ==================== 状态判定 ====================

    @Test
    @DisplayName("无异常 → status=0 成功，errorMsg 为空")
    void shouldRecordSuccessWhenNoException() throws Exception {
        OperationLogMessage message = captureLog(method("methodWithoutBizExceptions"), null, "result",
                new OperationLogProperties());

        assertThat(message.getStatus()).isEqualTo(OperationStatus.SUCCESS.getValue());
        assertThat(message.getErrorMsg()).isNull();
    }

    @Test
    @DisplayName("未配置任何业务异常 → 普通异常仍记 status=1 失败（与 1.0.0 行为一致）")
    void shouldRecordFailForUnknownException() throws Exception {
        OperationLogMessage message = captureLog(method("methodWithoutBizExceptions"),
                new RuntimeException("系统异常"), null, new OperationLogProperties());

        assertThat(message.getStatus()).isEqualTo(OperationStatus.FAIL.getValue());
        assertThat(message.getErrorMsg()).isEqualTo("系统异常");
    }

    @Test
    @DisplayName("注解声明业务异常 → status=2 业务失败，errorMsg 保留")
    void shouldRecordBizFailForAnnotationException() throws Exception {
        OperationLogMessage message = captureLog(method("methodWithBizException"),
                new BizException("余额不足"), null, new OperationLogProperties());

        assertThat(message.getStatus()).isEqualTo(OperationStatus.BIZ_FAIL.getValue());
        assertThat(message.getErrorMsg()).isEqualTo("余额不足");
    }

    @Test
    @DisplayName("全局配置声明业务异常 → status=2 业务失败")
    void shouldRecordBizFailForConfigException() throws Exception {
        OperationLogProperties properties = new OperationLogProperties();
        properties.setBusinessExceptions(List.of(BizException.class.getName()));

        OperationLogMessage message = captureLog(method("methodWithoutBizExceptions"),
                new BizException("校验失败"), null, properties);

        assertThat(message.getStatus()).isEqualTo(OperationStatus.BIZ_FAIL.getValue());
    }

    @Test
    @DisplayName("业务异常子类 → 子类自动命中 status=2")
    void shouldMatchSubclassOfBusinessException() throws Exception {
        OperationLogMessage message = captureLog(method("methodWithBizException"),
                new SubBizException("子类校验失败"), null, new OperationLogProperties());

        assertThat(message.getStatus()).isEqualTo(OperationStatus.BIZ_FAIL.getValue());
    }

    @Test
    @DisplayName("注解与全局配置取并集 → 任一命中即 status=2")
    void shouldUnionAnnotationAndConfig() throws Exception {
        OperationLogProperties properties = new OperationLogProperties();
        properties.setBusinessExceptions(List.of(OtherBizException.class.getName()));

        // 方法注解声明的是 BizException，抛出的是配置里的 OtherBizException
        OperationLogMessage message = captureLog(method("methodWithBizException"),
                new OtherBizException("配置命中的异常"), null, properties);

        assertThat(message.getStatus()).isEqualTo(OperationStatus.BIZ_FAIL.getValue());
    }

    @Test
    @DisplayName("配置类名无法加载 → warn 并忽略，不影响记录（仍按 status=1）")
    void shouldIgnoreUnloadableConfigClassName() throws Exception {
        OperationLogProperties properties = new OperationLogProperties();
        properties.setBusinessExceptions(List.of("com.example.NotExistsException"));

        OperationLogMessage message = captureLog(method("methodWithoutBizExceptions"),
                new RuntimeException("系统异常"), null, properties);

        assertThat(message.getStatus()).isEqualTo(OperationStatus.FAIL.getValue());
        assertThat(message.getErrorMsg()).isEqualTo("系统异常");
    }

    // ==================== 辅助方法 ====================

    /**
     * 模拟一次切面日志记录，捕获并返回发送的消息
     */
    private OperationLogMessage captureLog(Method method, Exception e, Object result,
                                           OperationLogProperties properties) {
        OperationLogAspect aspect = new OperationLogAspect(properties, sender, operatorResolver, paramDesensitizer);

        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        try (MockedStatic<RequestContextHolder> staticHolder = mockStatic(RequestContextHolder.class)) {
            ServletRequestAttributes attributes = mock(ServletRequestAttributes.class);
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getMethod()).thenReturn("POST");
            when(attributes.getRequest()).thenReturn(request);
            staticHolder.when(RequestContextHolder::getRequestAttributes).thenReturn(attributes);

            aspect.handleLog(joinPoint, e, result);
        }

        ArgumentCaptor<OperationLogMessage> captor = ArgumentCaptor.forClass(OperationLogMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    private static Method method(String name) throws NoSuchMethodException {
        return OperationLogAspectTest.class.getDeclaredMethod(name);
    }

    // ==================== 测试用注解方法与异常类型 ====================

    @OperationLog(title = "普通操作")
    void methodWithoutBizExceptions() {
    }

    @OperationLog(title = "业务操作", businessExceptions = BizException.class)
    void methodWithBizException() {
    }

    static class BizException extends RuntimeException {
        BizException(String message) {
            super(message);
        }
    }

    static class SubBizException extends BizException {
        SubBizException(String message) {
            super(message);
        }
    }

    static class OtherBizException extends RuntimeException {
        OtherBizException(String message) {
            super(message);
        }
    }
}
