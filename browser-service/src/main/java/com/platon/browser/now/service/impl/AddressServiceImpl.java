package com.platon.browser.now.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.platon.browser.client.PlatOnClient;
import com.platon.browser.client.RestrictingBalance;
import com.platon.browser.client.SpecialApi;
import com.platon.browser.common.BrowserConst;
import com.platon.browser.config.BlockChainConfig;
import com.platon.browser.dao.entity.Address;
import com.platon.browser.dao.entity.NetworkStat;
import com.platon.browser.dao.entity.RpPlan;
import com.platon.browser.dao.entity.RpPlanExample;
import com.platon.browser.dao.mapper.AddressMapper;
import com.platon.browser.dao.mapper.CustomRpPlanMapper;
import com.platon.browser.dao.mapper.RpPlanMapper;
import com.platon.browser.elasticsearch.BlockESRepository;
import com.platon.browser.elasticsearch.dto.Block;
import com.platon.browser.enums.I18nEnum;
import com.platon.browser.exception.BlankResponseException;
import com.platon.browser.exception.BusinessException;
import com.platon.browser.exception.ContractInvokeException;
import com.platon.browser.now.service.AddressService;
import com.platon.browser.now.service.cache.StatisticCacheService;
import com.platon.browser.req.address.QueryDetailRequest;
import com.platon.browser.req.address.QueryRPPlanDetailRequest;
import com.platon.browser.res.address.DetailsRPPlanResp;
import com.platon.browser.res.address.QueryDetailResp;
import com.platon.browser.res.address.QueryRPPlanDetailResp;
import com.platon.browser.util.I18nUtil;
import com.platon.sdk.contracts.ppos.dto.CallResponse;
import com.platon.sdk.contracts.ppos.dto.resp.RestrictingItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 地址具体逻辑实现方法
 *  @file AddressServiceImpl.java
 *  @description
 *	@author zhangrj
 *  @data 2019年8月31日
 */
@Service
public class AddressServiceImpl implements AddressService {

	private final Logger logger = LoggerFactory.getLogger(AddressServiceImpl.class);

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private RpPlanMapper rpPlanMapper;

    @Autowired
    private CustomRpPlanMapper customRpPlanMapper;

    @Autowired
    private PlatOnClient platonClient;

    @Autowired
    private I18nUtil i18n;

    @Autowired
    private BlockChainConfig blockChainConfig;
    
    @Autowired
	private BlockESRepository blockESRepository;
    
    @Autowired
    private SpecialApi specialApi;
    
    @Autowired
    private StatisticCacheService statisticCacheService;

    @Override
    public QueryDetailResp getDetails(QueryDetailRequest req) {
    	/** 根据主键查询地址信息 */
        Address item = addressMapper.selectByPrimaryKey(req.getAddress());
        QueryDetailResp resp = new QueryDetailResp();
        if (item != null) {
        	BeanUtils.copyProperties(item, resp);
        	resp.setDelegateUnlock(item.getDelegateHes());
        	/** 预先设置是否展示锁仓 */
        	resp.setIsRestricting(0);
        }
        /** 特殊账户余额直接查询链  */
	  	try {
	  		resp = this.getBalance(req, resp);
	  	} catch (Exception e) {
	  		logger.error("getBalance error",e);
	  		platonClient.updateCurrentWeb3jWrapper();
	  		try {
	  			resp = this.getBalance(req, resp);
			} catch (Exception e1) {
				logger.error("getBalance error again",e);
			} 
		}
        RpPlanExample rpPlanExample = new RpPlanExample();
		RpPlanExample.Criteria criteria = rpPlanExample.createCriteria();
		criteria.andAddressEqualTo(req.getAddress());
        List<RpPlan> rpPlans = rpPlanMapper.selectByExample(rpPlanExample);
        /** 有锁仓数据之后就可以返回1 */
        if(rpPlans != null && !rpPlans.isEmpty()) {
        	resp.setIsRestricting(1);
        }
       return resp;
    }

	@Override
	public QueryRPPlanDetailResp rpplanDetail(QueryRPPlanDetailRequest req) {
		QueryRPPlanDetailResp queryRPPlanDetailResp = new QueryRPPlanDetailResp();
		try {
			/**
			 * 链上实时查询对应的锁仓信息
			 */
			CallResponse<RestrictingItem> baseResponse = platonClient.getRestrictingPlanContract().getRestrictingInfo(req.getAddress()).send();
			if(baseResponse.isStatusOk()) {
				/**
				 * 可用余额为balance减去质押金额
				 */
				queryRPPlanDetailResp.setRestrictingBalance(new BigDecimal(baseResponse.getData().getBalance().subtract(baseResponse.getData().getPledge())));
				queryRPPlanDetailResp.setStakingValue(new BigDecimal(baseResponse.getData().getPledge()));
				queryRPPlanDetailResp.setUnderReleaseValue(new BigDecimal(baseResponse.getData().getDebt()));
			}
		} catch (Exception e) {
			logger.error("rpplanDetail error", e);
			throw new BusinessException(i18n.i(I18nEnum.SYSTEM_EXCEPTION));
		}
		/**
		 * 分页查询对应的锁仓计划
		 */
		RpPlanExample rpPlanExample = new RpPlanExample();
		RpPlanExample.Criteria criteria = rpPlanExample.createCriteria();
		criteria.andAddressEqualTo(req.getAddress());
		List<DetailsRPPlanResp> detailsRPPlanResps = new ArrayList<>();
		PageHelper.startPage(req.getPageNo(),req.getPageSize());
		Page<RpPlan> rpPlans = rpPlanMapper.selectByExample(rpPlanExample);
		for(RpPlan rPlan : rpPlans) {
			DetailsRPPlanResp detailsRPPlanResp = new DetailsRPPlanResp();
			BeanUtils.copyProperties(rPlan, detailsRPPlanResp);
			/**
			 * 锁仓周期对应快高  结算周期数 * epoch  + number,如果不是整数倍则为：结算周期 * （epoch-1）  + 多余的数目
			 */
			Long number;
			long remainder = rPlan.getNumber() % blockChainConfig.getSettlePeriodBlockCount().longValue();
			if(remainder == 0l) {
				number = blockChainConfig.getSettlePeriodBlockCount()
						.multiply(BigInteger.valueOf(rPlan.getEpoch())).add(BigInteger.valueOf(rPlan.getNumber())).longValue();
			} else {
				number = blockChainConfig.getSettlePeriodBlockCount()
						.multiply(BigInteger.valueOf(rPlan.getEpoch() - 1)).add(BigInteger.valueOf(rPlan.getNumber()))
						.add(blockChainConfig.getSettlePeriodBlockCount().subtract(BigInteger.valueOf(remainder))).longValue();
			}

			detailsRPPlanResp.setBlockNumber(number);
			/** 预计时间：预计块高减去当前块高乘以出块时间再加上区块时间 */
			Block block = null;
			try {
				block = blockESRepository.get(String.valueOf(rPlan.getNumber()), Block.class);
			} catch (IOException e) {
				logger.error("获取区块错误。", e);
			}
			if(block!=null) {
				NetworkStat networkStat = statisticCacheService.getNetworkStatCache();
				detailsRPPlanResp.setEstimateTime(new BigDecimal(networkStat.getAvgPackTime()).multiply(BigDecimal.valueOf(number - rPlan.getNumber()))
				.add(BigDecimal.valueOf(block.getTime().getTime())).longValue());
			}
			detailsRPPlanResps.add(detailsRPPlanResp);
		}
		queryRPPlanDetailResp.setRpPlans(detailsRPPlanResps);
		/**
		 * 获取计算总数
		 */
		BigDecimal bigDecimal = customRpPlanMapper.selectSumByAddress(req.getAddress());
		if(bigDecimal != null) {
			queryRPPlanDetailResp.setTotalValue(bigDecimal);
		}
		/**
		 * 获取列表总数
		 */
		queryRPPlanDetailResp.setTotal(rpPlans.getTotal());
		return queryRPPlanDetailResp;
	}
	
	private QueryDetailResp getBalance(QueryDetailRequest req, QueryDetailResp resp) throws ContractInvokeException, BlankResponseException, IOException {
		List<RestrictingBalance> restrictingBalances = specialApi.getRestrictingBalance(platonClient.getWeb3jWrapper().getWeb3j(), req.getAddress());
		if(restrictingBalances != null && !restrictingBalances.isEmpty()) {
			resp.setBalance(new BigDecimal(restrictingBalances.get(0).getFreeBalance()));
			resp.setRestrictingBalance(new BigDecimal(restrictingBalances.get(0).getLockBalance().subtract(restrictingBalances.get(0).getPledgeBalance())));
		}
		/** 特殊账户余额直接查询链  */
		if(BrowserConst.ACCOUNT.contains(req.getAddress())) {
			BigInteger balance = platonClient.getWeb3jWrapper().getWeb3j().platonGetBalance(req.getAddress(),DefaultBlockParameterName.LATEST).send().getBalance();
			resp.setBalance(new BigDecimal(balance));
		}
		return resp;
	}
}
