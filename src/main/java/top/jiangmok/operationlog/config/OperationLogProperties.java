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

    /** 存储位置：file | mysql | es，默认 file（零依赖起步） */
    private String saveLocation = "file";

    /** 异步策略：async（默认）| rabbitmq */
    private String asyncStrategy = "async";

    /** 文件存储配置（仅 file 模式生效） */
    private FileProperties file = new FileProperties();

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

    public FileProperties getFile() {
        return file;
    }

    public void setFile(FileProperties file) {
        this.file = file;
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
                ", file=" + file +
                ", taskExecutor=" + taskExecutor +
                '}';
    }

    /**
     * 文件存储配置
     */
    public static class FileProperties {

        /** 日志文件目录，默认 ./logs/operation-logs */
        private String logDir = "./logs/operation-logs";

        /** 滚动策略：daily（按天）| none（单文件），默认 daily */
        private String rollover = "daily";

        /** 最大保留天数，默认 90（仅 daily 模式生效） */
        private int maxRetentionDays = 90;

        // ---- Getter/Setter ----

        public String getLogDir() {
            return logDir;
        }

        public void setLogDir(String logDir) {
            this.logDir = logDir;
        }

        public String getRollover() {
            return rollover;
        }

        public void setRollover(String rollover) {
            this.rollover = rollover;
        }

        public int getMaxRetentionDays() {
            return maxRetentionDays;
        }

        public void setMaxRetentionDays(int maxRetentionDays) {
            this.maxRetentionDays = maxRetentionDays;
        }

        @Override
        public String toString() {
            return "FileProperties{" +
                    "logDir='" + logDir + '\'' +
                    ", rollover='" + rollover + '\'' +
                    ", maxRetentionDays=" + maxRetentionDays +
                    '}';
        }
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
