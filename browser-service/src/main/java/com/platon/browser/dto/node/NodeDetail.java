package com.platon.browser.dto.node;

import com.platon.browser.dao.entity.NodeRanking;
import com.platon.browser.util.EnergonUtil;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class NodeDetail {
    private Long id;
    private String nodeId;
    private String address;
    private String name;
    private String logo;
    private Integer electionStatus;
    private String location;
    private Long joinTime;
    private String deposit;
    private Double rewardRatio;
    private Integer ranking;
    private String profitAmount;
    private Integer verifyCount;
    private Long blockCount;
    private Double avgBlockTime;
    private String rewardAmount;
    private String blockReward;
    private String nodeUrl;
    private String publicKey;
    private String wallet;
    private String intro;
    private String orgName;
    private String orgWebsite;
    private int ticketCount;
    private Long beginNumber;
    private Long endNumber;
    private Long hitCount;
    private Long ticketEpoch;

    public void init(NodeRanking initData) {
        BeanUtils.copyProperties(initData,this);
        this.setJoinTime(initData.getJoinTime().getTime());
        this.setNodeUrl("http://"+initData.getIp()+":"+initData.getPort());
        // 公钥就是节点ID
        this.setPublicKey(initData.getNodeId());
        // 钱包就是address
        this.setWallet(initData.getAddress());
        this.setLogo(initData.getUrl());
        this.setAvgBlockTime(initData.getAvgTime());
        BigDecimal v = Convert.fromWei(initData.getProfitAmount(), Convert.Unit.ETHER);
        this.setProfitAmount(EnergonUtil.format(v));
        v = Convert.fromWei(initData.getRewardAmount(), Convert.Unit.ETHER);
        this.setRewardAmount(EnergonUtil.format(v));
        v = Convert.fromWei(initData.getBlockReward(), Convert.Unit.ETHER);
        this.setBlockReward(EnergonUtil.format(v));
        v = Convert.fromWei(initData.getDeposit(), Convert.Unit.ETHER);
        this.setDeposit(EnergonUtil.format(v));
        v = BigDecimal.ONE.subtract(BigDecimal.valueOf(initData.getRewardRatio())).setScale(2, RoundingMode.DOWN);
        this.setRewardRatio(v.doubleValue());
        this.setLogo(initData.getUrl());
    }
}