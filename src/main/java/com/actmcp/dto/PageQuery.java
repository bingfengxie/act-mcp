package com.actmcp.dto;

/**
 * 分页查询公共参数。
 */
public abstract class PageQuery {

    private int page = 1;
    private int size = 10;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
