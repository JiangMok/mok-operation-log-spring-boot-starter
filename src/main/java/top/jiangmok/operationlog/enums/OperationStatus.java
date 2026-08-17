package top.jiangmok.operationlog.enums;

/**
 * 操作状态
 *
 * @author mok
 */
public enum OperationStatus {

    /** 成功 */
    SUCCESS(0, "成功"),

    /** 失败（系统异常） */
    FAIL(1, "失败"),

    /** 业务失败（业务校验未通过等非系统异常） */
    BIZ_FAIL(2, "业务失败");

    private final Integer value;
    private final String description;

    OperationStatus(Integer value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 获取状态值（对应数据库 status 字段） */
    public Integer getValue() {
        return value;
    }

    /** 获取状态描述 */
    public String getDescription() {
        return description;
    }
}
