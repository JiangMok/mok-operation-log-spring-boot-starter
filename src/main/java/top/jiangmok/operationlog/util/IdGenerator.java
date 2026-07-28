package top.jiangmok.operationlog.util;

import java.util.UUID;

/**
 * ID 生成工具
 * <p>
 * 使用 JDK UUID 生成 32 位无连字符的小写 UUID，
 * 替代 Hutool 的 IdUtil.simpleUUID()。
 * </p>
 *
 * @author mok
 */
public class IdGenerator {

    /**
     * 生成 32 位无连字符的小写 UUID
     *
     * @return 如 "a1b2c3d4e5f6789012345678901234ab"
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
