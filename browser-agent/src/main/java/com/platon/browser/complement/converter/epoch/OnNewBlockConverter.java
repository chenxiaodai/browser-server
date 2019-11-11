package com.platon.browser.complement.converter.epoch;

import com.platon.browser.common.complement.cache.NetworkStatCache;
import com.platon.browser.common.complement.cache.NodeCache;
import com.platon.browser.complement.dao.param.epoch.NewBlock;
import com.platon.browser.common.queue.collection.event.CollectionEvent;
import com.platon.browser.complement.dao.mapper.NewBlockMapper;
import com.platon.browser.elasticsearch.dto.Block;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class OnNewBlockConverter {
	
    @Autowired
    private NodeCache nodeCache;
    
    @Autowired
    private NewBlockMapper newBlockMapper;

    @Autowired
    private NetworkStatCache networkStatCache;

	public void convert(CollectionEvent event, Block block) throws Exception {

        long startTime = System.currentTimeMillis();

	    networkStatCache.getNetworkStat().setCurNumber(event.getBlock().getNum());
      
		NewBlock newBlock = NewBlock.builder()
                .nodeId(block.getNodeId())
                .stakingBlockNum(nodeCache.getNode(block.getNodeId()).getStakingBlockNum())
                .blockRewardValue(new BigDecimal(event.getEpochMessage().getBlockReward()))
                .feeRewardValue(block.getTxFee())
                .build();
        
		newBlockMapper.newBlock(newBlock);

        log.debug("处理耗时:{} ms",System.currentTimeMillis()-startTime);
	}

}