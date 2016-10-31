package com.uxin.page;

import java.io.Serializable;

/**
 * 数据库、内存分页对象. 包含当前页数据及分页信息，P为入参的类型。
 * 
 * @author: smartlv
 * @date 2014年2月20日 上午10:05:40
 */
public class PageParam<T> implements Serializable
{
    private static final long serialVersionUID = 1L;

    public final static int DEFAULT_PAGE_No = 1;
    public final static int DEFAULT_PAGE_SIZE = 20;

    /**
     * 以Bean形式包装查询条件（不含参数：页号，每页记录数，总记录数，总分页数，是否有前一页，否有下一页）
     */
    private T p;

    /**
     * 跳转页数，页数是从第一页是从1开始计算的
     */
    private int pageNo = DEFAULT_PAGE_No;

    /**
     * 每页的记录数(每页尺寸)
     */
    private int pageSize = DEFAULT_PAGE_SIZE;

    private Long cursor;

    public PageParam()
    {

    }

    public PageParam(int pageNo, int pageSize)
    {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public PageParam(T p, int pageNo, int pageSize)
    {
        super();
        this.p = p;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public PageParam(T p, int pageNo, int pageSize, Long cursor)
    {
        this(p, pageNo, pageSize);
        this.cursor = cursor;
    }

    public T getP()
    {
        return p;
    }

    public void setP(T p)
    {
        this.p = p;
    }

    public int getPageNo()
    {
        return pageNo;
    }

    public void setPageNo(int pageNo)
    {
        this.pageNo = pageNo;
    }

    public void setPageNo(Integer pageNo)
    {
        if (pageNo != null)
        {
            this.pageNo = pageNo;
        }
    }

    public int getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(int pageSize)
    {
        this.pageSize = pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        if (pageSize != null)
        {
            this.pageSize = pageSize;
        }
    }

    public int getOffset()
    {
        return (getPageNo() - 1) * getPageSize();
    }

    public Long getCursor()
    {
        return cursor;
    }

    public void setCursor(Long cursor)
    {
        this.cursor = cursor;
    }

    @Override
    public String toString()
    {
        return "PageParam [p=" + p + ", pageNo=" + pageNo + ", pageSize=" + pageSize + ", cursor=" + cursor + "]";
    }

}
