package com.uxin.page;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.uxin.mybatis.paginator.domain.PageBounds;
import com.uxin.mybatis.paginator.domain.PageList;
import com.uxin.mybatis.paginator.domain.Paginator;

/**
 * 带记录总数的分页有关对象转换器，本类依赖mybatis-paginator-1.2.13包
 * 
 * @author smartlv
 * @date 2014年8月4日
 */
public class PageConvertor
{
    /**
     * 带返回总数的分页
     * 
     * @param page
     * @return
     * @author smartlv
     * @date 2014年8月4日
     */
    public static <T> PageBounds toPageBounds(PageParam<T> page)
    {
        PageBounds pb = new PageBounds(page.getPageNo(), page.getPageSize());
        return pb;
    }

    public static <T> PageData<T> toPageData(PageList<T> list)
    {
        Paginator paginator = list.getPaginator();
        return new PageData<T>(paginator.getPage(), paginator.getLimit(), paginator.getTotalCount(), list);
    }

    public static <T, S> PageData<T> toPageData(PageList<S> list, Function<S, T> trans)
    {
        Paginator paginator = list.getPaginator();
        return new PageData(paginator.getPage(), paginator.getLimit(), paginator.getTotalCount(),
                Lists.newArrayList(Lists.transform(list, trans)));
    }
}
