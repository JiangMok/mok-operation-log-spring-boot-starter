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
}
