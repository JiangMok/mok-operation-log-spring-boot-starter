package top.jiangmok.operationlog.model;

import top.jiangmok.operationlog.entity.OperationLogEntity;

import java.util.Collections;
import java.util.List;

/**
 * 与接入方响应结构无关的操作日志分页结果。
 *
 * @author mok
 */
public class OperationLogPageResult {

    private List<OperationLogEntity> records;
    private long total;
    private int pageNum;
    private int pageSize;

    public OperationLogPageResult() {
        this.records = Collections.emptyList();
    }

    public OperationLogPageResult(List<OperationLogEntity> records,
                                  long total, int pageNum, int pageSize) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public List<OperationLogEntity> getRecords() {
        return records;
    }

    public void setRecords(List<OperationLogEntity> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}

