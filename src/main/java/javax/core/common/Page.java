package javax.core.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Page<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_PAGE_SIZE = 20;

    // 每页显示的记录数
    private int pageSize = DEFAULT_PAGE_SIZE;

    // 当前页第一条数据在Lst中的位置，从0开始
    private long start;

    // 当前页中存放的记录，类型一般为List
    private List<T> rows;

    // 总共记录数
    private long total;

    public Page(){
        this(DEFAULT_PAGE_SIZE, 0, new ArrayList<T>(), 0);
    }

    /**
     * 默认构造函数
     *
     * @param pageSize：页面容量
     * @param start：本页数据在数据库中的起始位置
     * @param rows：本页包含的数据
     * @param total：数据库中总记录条数
     */
    public Page(int pageSize, long start, List<T> rows, long total) {
        this.pageSize = pageSize;
        this.start = start;
        this.rows = rows;
        this.total = total;
    }

    // 获取每页显示的数据条数
    public int getPageSize() {
        return pageSize;
    }

    // 获取当前页面的页码，从页码1开始
    public long getPageNo() {
        return start / pageSize + 1;
    }

    // 该页是否有下一页
    public boolean hasNextPage(){
        return this.getPageNo() < this.getTotalPageCount() - 1;
    }

    // 该页是否有上一页
    public boolean hasPreviousPage(){
        return this.getPageNo() > 1;
    }

    // 获取当前页面中的记录
    public List<T> getRows() {
        return rows;
    }

    public void setRows(List<T> rows) {
        this.rows = rows;
    }

    // 获取总记录数
    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    // 获取总页数
    public long getTotalPageCount(){
        if(total % pageSize == 0){
            return total / pageSize;
        } else {
            return  total / pageSize + 1;
        }
    }

    // 获取任一页第一条数据在数据集的位置，每页条数使用默认值
    protected static int getStartOfPage(int pageNo){
        return getStartOfPage(pageNo, DEFAULT_PAGE_SIZE);
    }

    public static int getStartOfPage(int pageNo, int pageSize){
        return (pageNo - 1) * pageSize;
    }
}
