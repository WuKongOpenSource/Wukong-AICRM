package com.kakarote.ai_crm.entity.VO;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class TokenPurchaseOrderVO implements Serializable {

    private String orderNo;

    private String planId;

    private String planName;

    private Long creditAmount;

    private Integer amountFen;

    private String amountDisplay;

    private String paymentChannel;

    private String paymentChannelLabel;

    private String status;

    private String qrCodeContent;

    private String qrCodeImage;

    private String paymentMode;

    private String paymentFormHtml;

    private Date expireTime;

    private Date paidTime;

    private Date createTime;
}
