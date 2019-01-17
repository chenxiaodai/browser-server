package com.platon.browser.dto.transaction;

import com.platon.browser.dao.entity.PendingTx;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class PendingTxItem {
    private String txHash;
    private long timestamp;
    private String energonLimit;
    private String energonPrice;
    private String priceInE;
    private String priceInEnergon;
    private String from;
    private String to;
    private String value;
    private String txType;
    private long serverTime;
    private String receiveType;

    public void init(PendingTx initData){
        BeanUtils.copyProperties(initData,this);
        this.setTxHash(initData.getHash());
        this.setTimestamp(initData.getTimestamp().getTime());
        this.setServerTime(System.currentTimeMillis());
        BigDecimal v = Convert.fromWei(initData.getValue(), Convert.Unit.ETHER).setScale(18, RoundingMode.DOWN);
        this.setValue(String.valueOf(v.doubleValue()));
        this.setPriceInE(initData.getEnergonPrice());
        v = Convert.fromWei(initData.getEnergonPrice(), Convert.Unit.ETHER).setScale(18,RoundingMode.DOWN);
        this.setPriceInEnergon(String.valueOf(v.doubleValue()));
    }
}
