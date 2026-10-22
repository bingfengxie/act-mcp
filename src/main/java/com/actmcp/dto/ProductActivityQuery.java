package com.actmcp.dto;

/**
 * 商品活动列表查询参数。
 */
public class ProductActivityQuery extends PageQuery {

    private Long actId;
    private Integer actType;
    private String keyword;

    public Long getActId() {
        return actId;
    }

    public void setActId(Long actId) {
        this.actId = actId;
    }

    public Integer getActType() {
        return actType;
    }

    public void setActType(Integer actType) {
        this.actType = actType;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
