package top.jiangmok.operationlog.desensitize;

/**
 * 参数脱敏 SPI 接口
 * <p>
 * 对 JSON 参数字符串进行脱敏处理，屏蔽敏感字段。
 * 内置 DefaultParamDesensitizer 会处理常见的敏感字段（password/token/secret 等）。
 * 用户可实现此接口并注册为 Spring Bean 来自定义脱敏规则。
 * </p>
 *
 * @author mok
 */
public interface ParamDesensitizer {

    /**
     * 对 JSON 参数字符串进行脱敏
     *
     * @param jsonParam 原始 JSON 参数字符串
     * @return 脱敏后的 JSON 字符串
     */
    String desensitize(String jsonParam);
}
