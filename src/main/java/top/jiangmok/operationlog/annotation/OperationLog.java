package top.jiangmok.operationlog.annotation;

import top.jiangmok.operationlog.enums.BusinessType;

import java.lang.annotation.*;

/**
 * 操作日志注解
 *
 * @author mok
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 接口标题 */
    String title() default "";

    /** 操作类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否保存请求参数 */
    boolean saveRequestParam() default true;

    /** 是否保存响应参数 */
    boolean saveResponseData() default true;

    /**
     * 业务异常类型（子类自动匹配），默认空
     * <p>抛出这些异常的方法记录为"业务失败"（status=2），而非"失败"（status=1）。
     * 与全局配置 {@code mok.operation-log.business-exceptions} 取并集。</p>
     */
    Class<? extends Throwable>[] businessExceptions() default {};
}
