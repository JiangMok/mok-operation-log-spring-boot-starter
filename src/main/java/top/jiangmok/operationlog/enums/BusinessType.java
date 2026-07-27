package top.jiangmok.operationlog.enums;

/**
 * 业务操作类型
 *
 * @author mok
 */
public enum BusinessType {

    LOGIN("登录"),
    LOGOUT("登出"),
    OTHER("其它"),
    QUERY("查询"),
    INSERT("新增"),
    UPDATE("修改"),
    DELETE("删除"),
    GRANT("授权"),
    EXPORT("导出"),
    IMPORT("导入"),
    FORCE("强退"),
    CLEAN("清空数据");

    private final String value;

    BusinessType(String value) {
        this.value = value;
    }

    /** 获取业务操作类型描述 */
    public String getValue() {
        return value;
    }

    /** 根据 value 获取对应的枚举 */
    public static BusinessType getByValue(String value) {
        for (BusinessType type : BusinessType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }

    /** 根据 name 获取对应的枚举 */
    public static BusinessType getByName(String name) {
        try {
            return BusinessType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
