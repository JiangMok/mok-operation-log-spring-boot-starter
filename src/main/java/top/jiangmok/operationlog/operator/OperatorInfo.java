package top.jiangmok.operationlog.operator;

/**
 * 一次操作中的操作人信息快照。
 *
 * @author mok
 */
public class OperatorInfo {

    private String operatorId;
    private String operatorName;
    private String operatorType;
    private String deptName;

    public OperatorInfo() {
    }

    public OperatorInfo(String operatorId, String operatorName,
                        String operatorType, String deptName) {
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.operatorType = operatorType;
        this.deptName = deptName;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }
}

