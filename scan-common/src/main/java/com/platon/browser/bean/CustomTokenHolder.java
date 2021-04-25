package com.platon.browser.bean;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomTokenHolder {

    private String tokenAddress;

    private String address;

    private String type;

    private String symbol;

    private String name;

    private BigDecimal totalSupply;

    private BigDecimal balance;

    private Integer decimal;

    private Integer txCount;

    private String tokenId;

}