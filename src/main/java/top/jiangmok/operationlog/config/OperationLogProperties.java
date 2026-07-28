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

    /** 异步策略：async（默认）| rabbitmq */
    private String asyncStrategy = "async";

    /** 线程池配置（仅 async 策略生效） */
    private TaskExecutorProperties taskExecutor = new TaskExecutorProperties();

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

    public String getAsyncStrategy() {
        return asyncStrategy;
    }

    public void setAsyncStrategy(String asyncStrategy) {
        this.asyncStrategy = asyncStrategy;
    }

    public TaskExecutorProperties getTaskExecutor() {
        return taskExecutor;
    }

    public void setTaskExecutor(TaskExecutorProperties taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Override
    public String toString() {
        return "OperationLogProperties{" +
                "enabled=" + enabled +
                ", recordGet=" + recordGet +
                ", maxContentLength=" + maxContentLength +
                ", saveLocation='" + saveLocation + '\'' +
                ", asyncStrategy='" + asyncStrategy + '\'' +
                ", taskExecutor=" + taskExecutor +
                '}';
    }

    /**
     * 线程池配置
     */
    public static class TaskExecutorProperties {

        /** 核心线程数，默认 2 */
        private int coreSize = 2;

        /** 最大线程数，默认 4 */
        private int maxSize = 4;

        /** 队列容量，默认 200 */
        private int queueCapacity = 200;

        // ---- Getter/Setter ----

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        @Override
        public String toString() {
            return "TaskExecutorProperties{" +
                    "coreSize=" + coreSize +
                    ", maxSize=" + maxSize +
                    ", queueCapacity=" + queueCapacity +
                    '}';
        }
    }
}
