package top.jiangmok.operationlog.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 操作日志实体
 *
 * @author mok
 */
@TableName("mok_operation_log")
public class OperationLogEntity {

    private String id;
    private String title;
    private String businessType;
    private String method;
    private String requestMethod;
    private String operatorType;
    private String operatorId;
    private String operatorName;
    private String deptName;
    private String operUrl;
    private String operIp;
    private String operLocation;
    private String operParam;
    private String jsonResult;
    private Integer status;             // 0-成功, 1-失败
    private String errorMsg;
    private LocalDateTime operTime;
    private LocalDateTime createTime;
    private Integer retryCount;

    public OperationLogEntity() {
    }

    // ---- Getter/Setter ----

    public String getId() {
        return id;
    }

    public OperationLogEntity setId(String id) {
        this.id = id;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public OperationLogEntity setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getBusinessType() {
        return businessType;
    }

    public OperationLogEntity setBusinessType(String businessType) {
        this.businessType = businessType;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public OperationLogEntity setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public OperationLogEntity setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
        return this;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public OperationLogEntity setOperatorType(String operatorType) {
        this.operatorType = operatorType;
        return this;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public OperationLogEntity setOperatorId(String operatorId) {
        this.operatorId = operatorId;
        return this;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public OperationLogEntity setOperatorName(String operatorName) {
        this.operatorName = operatorName;
        return this;
    }

    public String getDeptName() {
        return deptName;
    }

    public OperationLogEntity setDeptName(String deptName) {
        this.deptName = deptName;
        return this;
    }

    public String getOperUrl() {
        return operUrl;
    }

    public OperationLogEntity setOperUrl(String operUrl) {
        this.operUrl = operUrl;
        return this;
    }

    public String getOperIp() {
        return operIp;
    }

    public OperationLogEntity setOperIp(String operIp) {
        this.operIp = operIp;
        return this;
    }

    public String getOperLocation() {
        return operLocation;
    }

    public OperationLogEntity setOperLocation(String operLocation) {
        this.operLocation = operLocation;
        return this;
    }

    public String getOperParam() {
        return operParam;
    }

    public OperationLogEntity setOperParam(String operParam) {
        this.operParam = operParam;
        return this;
    }

    public String getJsonResult() {
        return jsonResult;
    }

    public OperationLogEntity setJsonResult(String jsonResult) {
        this.jsonResult = jsonResult;
        return this;
    }

    public Integer getStatus() {
        return status;
    }

    public OperationLogEntity setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public OperationLogEntity setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
        return this;
    }

    public LocalDateTime getOperTime() {
        return operTime;
    }

    public OperationLogEntity setOperTime(LocalDateTime operTime) {
        this.operTime = operTime;
        return this;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public OperationLogEntity setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
        return this;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public OperationLogEntity setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationLogEntity that = (OperationLogEntity) o;
        return Objects.equals(id, that.id)
                && Objects.equals(title, that.title)
                && Objects.equals(businessType, that.businessType)
                && Objects.equals(method, that.method)
                && Objects.equals(requestMethod, that.requestMethod)
                && Objects.equals(operatorType, that.operatorType)
                && Objects.equals(operatorId, that.operatorId)
                && Objects.equals(operatorName, that.operatorName)
                && Objects.equals(deptName, that.deptName)
                && Objects.equals(operUrl, that.operUrl)
                && Objects.equals(operIp, that.operIp)
                && Objects.equals(operLocation, that.operLocation)
                && Objects.equals(operParam, that.operParam)
                && Objects.equals(jsonResult, that.jsonResult)
                && Objects.equals(status, that.status)
                && Objects.equals(errorMsg, that.errorMsg)
                && Objects.equals(operTime, that.operTime)
                && Objects.equals(createTime, that.createTime)
                && Objects.equals(retryCount, that.retryCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, businessType, method, requestMethod, operatorType,
                operatorId, operatorName, deptName, operUrl, operIp, operLocation,
                operParam, jsonResult, status, errorMsg, operTime, createTime, retryCount);
    }

    @Override
    public String toString() {
        return "OperationLogEntity{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", businessType='" + businessType + '\'' +
                ", method='" + method + '\'' +
                ", requestMethod='" + requestMethod + '\'' +
                ", operatorType='" + operatorType + '\'' +
                ", operatorId='" + operatorId + '\'' +
                ", operatorName='" + operatorName + '\'' +
                ", deptName='" + deptName + '\'' +
                ", operUrl='" + operUrl + '\'' +
                ", operIp='" + operIp + '\'' +
                ", operLocation='" + operLocation + '\'' +
                ", operParam='" + operParam + '\'' +
                ", jsonResult='" + jsonResult + '\'' +
                ", status=" + status +
                ", errorMsg='" + errorMsg + '\'' +
                ", operTime=" + operTime +
                ", createTime=" + createTime +
                ", retryCount=" + retryCount +
                '}';
    }
}
