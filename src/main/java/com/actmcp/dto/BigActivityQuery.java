package com.actmcp.dto;

/**
 * 大活动列表查询参数。
 */
public class BigActivityQuery extends PageQuery {

    private String keyword;
    private Integer actType;
    private String audienceScope;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getActType() {
        return actType;
    }

    public void setActType(Integer actType) {
        this.actType = actType;
    }

    public String getAudienceScope() {
        return audienceScope;
    }

    public void setAudienceScope(String audienceScope) {
        this.audienceScope = audienceScope;
    }
}
