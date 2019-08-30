package com.platon.browser.now.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.platon.browser.common.BrowserConst;
import com.platon.browser.dao.entity.DelegationExample;
import com.platon.browser.dao.entity.DelegationStaking;
import com.platon.browser.dao.entity.NodeOpt;
import com.platon.browser.dao.entity.NodeOptExample;
import com.platon.browser.dao.entity.StakingNode;
import com.platon.browser.dao.mapper.CustomStakingMapper;
import com.platon.browser.dao.mapper.DelegationMapper;
import com.platon.browser.dao.mapper.NodeOptMapper;
import com.platon.browser.dto.RespPage;
import com.platon.browser.enums.I18nEnum;
import com.platon.browser.enums.IsConsensusStatus;
import com.platon.browser.enums.RetEnum;
import com.platon.browser.enums.StakingStatus;
import com.platon.browser.enums.StakingStatusEnum;
import com.platon.browser.now.service.StakingService;
import com.platon.browser.now.service.cache.StatisticCacheService;
import com.platon.browser.redis.dto.NetworkStatRedis;
import com.platon.browser.req.staking.AliveStakingListReq;
import com.platon.browser.req.staking.DelegationListByAddressReq;
import com.platon.browser.req.staking.DelegationListByStakingReq;
import com.platon.browser.req.staking.HistoryStakingListReq;
import com.platon.browser.req.staking.StakingDetailsReq;
import com.platon.browser.req.staking.StakingOptRecordListReq;
import com.platon.browser.res.BaseResp;
import com.platon.browser.resp.staking.AliveStakingListResp;
import com.platon.browser.resp.staking.DelegationListByAddressResp;
import com.platon.browser.resp.staking.DelegationListByStakingResp;
import com.platon.browser.resp.staking.HistoryStakingListResp;
import com.platon.browser.resp.staking.StakingChangeNewResp;
import com.platon.browser.resp.staking.StakingDetailsResp;
import com.platon.browser.resp.staking.StakingOptRecordListResp;
import com.platon.browser.resp.staking.StakingStatisticNewResp;
import com.platon.browser.util.I18nUtil;

@Service
public class StakingServiceImpl implements StakingService {

	@Autowired
	private StatisticCacheService statisticCacheService;

	@Autowired
	private CustomStakingMapper customStakingMapper;
	
	@Autowired
	private NodeOptMapper nodeOptMapper;
	
	@Autowired
	private DelegationMapper delegationMapper;

	@Autowired
	private I18nUtil i18n;
	
	@Override
	public StakingStatisticNewResp stakingStatisticNew() {
		NetworkStatRedis networkStatRedis = statisticCacheService.getNetworkStatCache();
		StakingStatisticNewResp stakingStatisticNewResp = new StakingStatisticNewResp();
		if(networkStatRedis != null) {
			BeanUtils.copyProperties(networkStatRedis, stakingStatisticNewResp);
		}
		return stakingStatisticNewResp;
	}

	@Override
	public RespPage<AliveStakingListResp> aliveStakingList(AliveStakingListReq req) {
		PageHelper.startPage(req.getPageNo(), req.getPageSize());
		Integer status = null;
		Integer isConsensus = null;
		String name = req.getKey();
		switch (StakingStatusEnum.valueOf(req.getQueryStatus().toUpperCase())) {
			case ALL:
				break;
			case ACTIVE:
				//活跃中代表即使后续同时也是共识周期验证人
				status = StakingStatusEnum.ACTIVE.getCode();
				isConsensus = IsConsensusStatus.YES.getCode();
				break;
			case CANDIDATE:
				status = StakingStatusEnum.CANDIDATE.getCode();
				break;
			default:
				break;
		}

		RespPage<AliveStakingListResp> respPage = new RespPage<>();
		List<AliveStakingListResp> lists = new LinkedList<AliveStakingListResp>();
		//根据条件和状态进行查询列表
		Page<StakingNode> stakingPage = customStakingMapper.selectStakingAndNodeByExample(null, name, status, isConsensus,null);
		List<StakingNode> stakings = stakingPage.getResult();
		//查询出块节点
		NetworkStatRedis networkStatRedis = statisticCacheService.getNetworkStatCache();
		for (int i = 0; i < stakings.size(); i++) {
			AliveStakingListResp aliveStakingListResp = new AliveStakingListResp();
			BeanUtils.copyProperties(stakings.get(i), aliveStakingListResp);
			aliveStakingListResp.setBlockQty(stakings.get(i).getCurConsBlockQty());
			aliveStakingListResp.setDelegateQty(stakings.get(i).getStatDelegateQty());
			//委托总金额数=委托交易总金额(犹豫期金额)+委托交易总金额(锁定期金额)
			String sumAmount = new BigDecimal(stakings.get(i).getStatDelegateHas())
					.add(new BigDecimal(stakings.get(i).getStatDelegateLocked())).toString();
			aliveStakingListResp.setDelegateValue(sumAmount);
			aliveStakingListResp.setIsInit(stakings.get(i).getIsInit().intValue() == 1?true:false);
			if(stakings.get(i).getIsRecommend() != null) {
				aliveStakingListResp.setIsRecommend(Integer.valueOf(1).compareTo(stakings.get(i).getIsRecommend()) == 0?true:false);
			}
			aliveStakingListResp.setRanking(i + 1);
			aliveStakingListResp.setSlashLowQty(stakings.get(i).getStatSlashLowQty());
			aliveStakingListResp.setSlashMultiQty(stakings.get(i).getStatSlashMultiQty());
			if(stakings.get(i).getNodeId().equals(networkStatRedis.getNodeId())) {
				aliveStakingListResp.setStatus(StakingStatusEnum.BLOCK.getCode());
			} else {
				aliveStakingListResp.setStatus(StakingStatusEnum.getCodeByStatus(stakings.get(i).getStatus(), stakings.get(i).getIsConsensus()));
			}
			
			aliveStakingListResp.setNodeName(stakings.get(i).getStakingName());
			//质押总数=有效的质押+委托
			String totalValue = new BigDecimal(stakings.get(i).getStakingHas()).add(new BigDecimal(stakings.get(i).getStakingLocked()))
					.add(new BigDecimal(stakings.get(i).getStatDelegateHas())).add(new BigDecimal(stakings.get(i).getStatDelegateLocked())).toString();
			aliveStakingListResp.setTotalValue(totalValue);
			lists.add(aliveStakingListResp);
		}
		Page<?> page = new Page<>(req.getPageNo(), req.getPageSize());
		page.setTotal(stakingPage.getTotal());
		respPage.init(page, lists);
		return respPage;
	}

	@Override
	public RespPage<HistoryStakingListResp> historyStakingList(HistoryStakingListReq req) {
		PageHelper.startPage(req.getPageNo(), req.getPageSize());
		List<Integer> status = new ArrayList<Integer>();
		status.add(StakingStatus.ABORTING.getCode());
		status.add(StakingStatus.EXITED.getCode());
		RespPage<HistoryStakingListResp> respPage = new RespPage<>();
		List<HistoryStakingListResp> lists = new LinkedList<HistoryStakingListResp>();
		//根据条件和状态进行查询列表
		Page<StakingNode> stakings = customStakingMapper.selectStakingAndNodeByExample(null, req.getKey(), null, null, status);
		for (StakingNode stakingNode:stakings.getResult()) {
			HistoryStakingListResp historyStakingListResp = new HistoryStakingListResp();
			BeanUtils.copyProperties(stakingNode, historyStakingListResp);
			if(stakingNode.getLeaveTime()!=null) {
				historyStakingListResp.setLeaveTime(stakingNode.getLeaveTime().getTime());
			}
			historyStakingListResp.setBlockQty(stakingNode.getCurConsBlockQty());
			historyStakingListResp.setNodeName(stakingNode.getStakingName());
			historyStakingListResp.setSlashLowQty(stakingNode.getStatSlashLowQty());
			historyStakingListResp.setSlashMultiQty(stakingNode.getStatSlashMultiQty());
			historyStakingListResp.setStatus(StakingStatusEnum.getCodeByStatus(stakingNode.getStatus(), stakingNode.getIsConsensus()));
			lists.add(historyStakingListResp);
		}
		Page<?> page = new Page<>(req.getPageNo(), req.getPageSize());
		page.setTotal(stakings.getTotal());
		respPage.init(page, lists);
		return respPage;
	}

	@Override
	public BaseResp<StakingChangeNewResp> stakingChangeNew() {
		StakingChangeNewResp stakingChangeNewResp = new StakingChangeNewResp();
		return BaseResp.build(RetEnum.RET_SUCCESS.getCode(), i18n.i(I18nEnum.SUCCESS), stakingChangeNewResp);
	}

	@Override
	public BaseResp<StakingDetailsResp> stakingDetails(StakingDetailsReq req) {
		List<StakingNode> stakings = customStakingMapper.selectStakingAndNodeByExample(req.getNodeId(),null ,null, null, null);
		Integer size = stakings.size();
		StakingDetailsResp resp = new StakingDetailsResp();
		switch (size) {
			case 0:
				return BaseResp.build(RetEnum.RET_SYS_EXCEPTION.getCode(), i18n.i(I18nEnum.FAILURE), null);
			default: // 只有一条数据
				StakingNode stakingNode = stakings.get(0);
				BeanUtils.copyProperties(stakingNode, resp);
				resp.setIsInit(stakingNode.getIsInit().intValue() == 1?true:false);
				resp.setNodeName(stakingNode.getStakingName());
				resp.setStatus(StakingStatusEnum.getCodeByStatus(stakingNode.getStatus(), stakingNode.getIsConsensus()));
				//质押总数=有效的质押+委托
				String totalValue = new BigDecimal(stakingNode.getStakingHas()).add(new BigDecimal(stakingNode.getStakingLocked()))
						.add(new BigDecimal(stakingNode.getStatDelegateHas())).add(new BigDecimal(stakingNode.getStatDelegateLocked())).toString();
				resp.setTotalValue(totalValue);
				//委托总金额数=委托交易总金额(犹豫期金额)+委托交易总金额(锁定期金额)
				String delValue = new BigDecimal(stakingNode.getStatDelegateHas()).add(new BigDecimal(stakingNode.getStatDelegateLocked())).toString();
				resp.setDelegateValue(delValue);
				String stakingValue = new BigDecimal(stakingNode.getStakingHas()).add(new BigDecimal(stakingNode.getStakingLocked())).toString();
				resp.setStakingValue(stakingValue);
				resp.setDelegateQty(stakingNode.getStatDelegateQty());
				resp.setSlashLowQty(stakingNode.getStatSlashLowQty());
				resp.setSlashMultiQty(stakingNode.getStatSlashMultiQty());
				resp.setBlockQty(stakingNode.getStatBlockQty());
				resp.setExpectBlockQty(stakingNode.getStatExpectBlockQty());
				resp.setWebsite(stakingNode.getWebSite());
				resp.setExternalUrl(BrowserConst.EX_URL + stakingNode.getExternalName());
				resp.setVerifierTime(stakingNode.getStatVerifierTime());
				resp.setJoinTime(stakingNode.getJoinTime().getTime());
				if(StringUtils.isNotBlank(stakingNode.getStatRewardValue())) {
					resp.setRewardValue(stakingNode.getStatRewardValue());
				}
				if(stakingNode.getLeaveTime() != null) {
					resp.setLeaveTime(stakingNode.getLeaveTime().getTime());
				}
				break;
		}
		return BaseResp.build(RetEnum.RET_SUCCESS.getCode(), i18n.i(I18nEnum.SUCCESS), resp);
	}
	
	@Override
	public RespPage<StakingOptRecordListResp> stakingOptRecordList( StakingOptRecordListReq req) {
		NodeOptExample nodeOptExample = new NodeOptExample();
		PageHelper.startPage(req.getPageNo(), req.getPageSize());
		NodeOptExample.Criteria criteria = nodeOptExample.createCriteria();
		criteria.andNodeIdEqualTo(req.getNodeId());
		RespPage<StakingOptRecordListResp> respPage = new RespPage<>();
		List<StakingOptRecordListResp> lists = new LinkedList<StakingOptRecordListResp>();
		List<NodeOpt> nodeOpts = nodeOptMapper.selectByExample(nodeOptExample);
		for (NodeOpt nodeOpt: nodeOpts) {
			StakingOptRecordListResp stakingOptRecordListResp = new StakingOptRecordListResp();
			BeanUtils.copyProperties(nodeOpt, stakingOptRecordListResp);
			stakingOptRecordListResp.setTimestamp(nodeOpt.getCreateTime().getTime());
			lists.add(stakingOptRecordListResp);
		}
		long size = nodeOptMapper.countByExample(nodeOptExample);
		Page<?> page = new Page<>(req.getPageNo(), req.getPageSize());
		page.setTotal(size);
		respPage.init(page, lists);
		return respPage;
	}
	
	@Override
	public RespPage<DelegationListByStakingResp> delegationListByStaking( DelegationListByStakingReq req) {
		DelegationExample delegationExample = new DelegationExample();
		com.platon.browser.dao.entity.DelegationExample.Criteria criteria = delegationExample.createCriteria();
		criteria.andNodeIdEqualTo(req.getNodeId());
		criteria.andStakingBlockNumEqualTo(Long.parseLong(req.getStakingBlockNum()));
		PageHelper.startPage(req.getPageNo(), req.getPageSize());
		List<DelegationListByStakingResp> lists = new LinkedList<DelegationListByStakingResp>();
		
		List<DelegationStaking> delegationStakings = 
				delegationMapper.selectDelegationAndStakingByExample(req.getNodeId(),Long.parseLong(req.getStakingBlockNum()),null);
		String allDelegate = "0";
		String allLockDelegate = "0";
		for (DelegationStaking delegationStaking: delegationStakings) {
			DelegationListByStakingResp byStakingResp = new DelegationListByStakingResp();
			byStakingResp.setDelegateAddr(delegationStaking.getDelegateAddr());
			String delValue = new BigDecimal(delegationStaking.getDelegateHas())
					.add(new BigDecimal(delegationStaking.getDelegateLocked())).toString();
			byStakingResp.setDelegateValue(delValue);
			// "delegateLocked":"",    //已锁定委托（LAT）如果关联的验证人状态正常则正常显示，如果其他情况则为零（delegation）
			String deleLock = delegationStaking.getStatus()==2?delegationStaking.getDelegateLocked():"0";
			byStakingResp.setDelegateLocked(deleLock);
			
			allDelegate = new BigDecimal(allDelegate).add(new BigDecimal(delValue).add(new BigDecimal(deleLock))).toString();
			allLockDelegate = new BigDecimal(allLockDelegate).add(new BigDecimal(deleLock)).toString();
			byStakingResp.setAllDelegateLocked(allDelegate);
			byStakingResp.setDelegateTotalValue(allDelegate);
			lists.add(byStakingResp);
		}
		
		long size = delegationMapper.countByExample(delegationExample);
		Page<?> page = new Page<>(req.getPageNo(), req.getPageSize());
		page.setTotal(size);
		RespPage<DelegationListByStakingResp> respPage = new RespPage<>();
		respPage.init(page, lists);
		return respPage;
	}
	
	@Override
	public RespPage<DelegationListByAddressResp> delegationListByAddress( DelegationListByAddressReq req) {
		DelegationExample delegationExample = new DelegationExample();
		com.platon.browser.dao.entity.DelegationExample.Criteria criteria = delegationExample.createCriteria();
		criteria.andDelegateAddrEqualTo(req.getAddress());
		
		PageHelper.startPage(req.getPageNo(), req.getPageSize());
		List<DelegationListByAddressResp> lists = new LinkedList<DelegationListByAddressResp>();
		
		List<DelegationStaking> delegationStakings = 
				delegationMapper.selectDelegationAndStakingByExample(null,null,req.getAddress());
		for (DelegationStaking delegationStaking:  delegationStakings) {
			DelegationListByAddressResp byAddressResp = new DelegationListByAddressResp();
			BeanUtils.copyProperties(delegationStaking, byAddressResp);
			byAddressResp.setNodeName(delegationStaking.getStakingName());
			String deleValue = new BigDecimal(delegationStaking.getDelegateHas())
					.add(new BigDecimal(delegationStaking.getDelegateLocked())).toString();
			byAddressResp.setDelegateValue(deleValue);
			String deletgateHas = delegationStaking.getStatus()==2?delegationStaking.getDelegateHas():"0";
			byAddressResp.setDelegateHas(deletgateHas);
			String deletgateLock = delegationStaking.getStatus()==2?delegationStaking.getDelegateLocked():"0";
			byAddressResp.setDelegateLocked(deletgateLock);
			byAddressResp.setAllDelegateLocked(delegationStaking.getStatDelegateLocked());
			String deletgateUnLock = delegationStaking.getStatus()==2?"0":new BigDecimal(delegationStaking.getDelegateHas())
					.add(new BigDecimal(delegationStaking.getDelegateLocked())).toString();
			byAddressResp.setDelegateUnlock(deletgateUnLock);
			lists.add(byAddressResp);
		}
		long size = delegationMapper.countByExample(delegationExample);
		Page<?> page = new Page<>(req.getPageNo(), req.getPageSize());
		page.setTotal(size);
		RespPage<DelegationListByAddressResp> respPage = new RespPage<>();
		respPage.init(page, lists);
		return respPage;
	}

}
