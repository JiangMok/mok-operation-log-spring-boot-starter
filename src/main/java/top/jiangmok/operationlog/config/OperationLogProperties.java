package top.jiangmok.operationlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 操作日志配置属性
 *
 * @author mok
 */
@ConfigurationProperties(prefix = "mok.operation-log")
public class OperationLogProperties {

    /** 是否启用操作日志，默认 true */
    private Boolean enabled = true;

    /** 是否记录 GET 请求，默认 true */
    private Boolean recordGet = true;

    /** 参数/响应内容最大长度（超过则截断），默认 2000 */
    private Integer maxContentLength = 2000;

    /** 存储位置：mysql 或 es，默认 mysql */
    private String saveLocation = "mysql";

    // ---- Getter/Setter ----

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getRecordGet() {
        return recordGet;
    }

    public void setRecordGet(Boolean recordGet) {
        this.recordGet = recordGet;
    }

    public Integer getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(Integer maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public String getSaveLocation() {
        return saveLocation;
    }

    public void setSaveLocation(String saveLocation) {
        this.saveLocation = saveLocation;
    }

    @Override
    public String toString() {
        return "OperationLogProperties{" +
                "enabled=" + enabled +
                ", recordGet=" + recordGet +
                ", maxContentLength=" + maxContentLength +
                ", saveLocation='" + saveLocation + '\'' +
                '}';
    }
}
