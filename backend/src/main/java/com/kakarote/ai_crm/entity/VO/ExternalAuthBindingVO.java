package com.kakarote.ai_crm.entity.VO;

import lombok.Data;

import java.util.Date;

@Data
public class ExternalAuthBindingVO {

    private String provider;

    private String name;

    private String providerName;

    private Boolean bound;

    private Boolean enabled;

    private String subject;

    private String email;

    private String displayName;

    private String avatarUrl;

    private Date bindTime;

    private Date lastLoginTime;
}
