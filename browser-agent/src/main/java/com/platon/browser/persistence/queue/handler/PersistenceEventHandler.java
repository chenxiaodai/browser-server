package com.platon.browser.persistence.queue.handler;

import com.lmax.disruptor.EventHandler;
import com.platon.browser.common.complement.cache.NetworkStatCache;
import com.platon.browser.common.service.elasticsearch.EsImportService;
import com.platon.browser.common.service.redis.RedisImportService;
import com.platon.browser.common.utils.BakDataDeleteUtil;
import com.platon.browser.dao.entity.NetworkStat;
import com.platon.browser.elasticsearch.dto.Block;
import com.platon.browser.elasticsearch.dto.NodeOpt;
import com.platon.browser.elasticsearch.dto.Transaction;
import com.platon.browser.persistence.queue.event.PersistenceEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 区块事件处理器
 */
@Slf4j
@Component
public class PersistenceEventHandler implements EventHandler<PersistenceEvent> {

    @Autowired
    private EsImportService esImportService;
    @Autowired
    private RedisImportService redisImportService;
    @Autowired
    private NetworkStatCache networkStatCache;

    @Setter
    @Getter
    @Value("${disruptor.queue.persistence.batch-size}")
    private volatile int batchSize;

    // 处理的最大区块号
    @Getter
    private volatile long maxBlockNumber;

    private Set<Block> blockStage = new HashSet<>();
    private Set<Transaction> transactionStage = new HashSet<>();
    private Set<NodeOpt> nodeOptStage = new HashSet<>();

    @Override
    @Retryable(value = Exception.class, maxAttempts = Integer.MAX_VALUE,label = "PersistenceEventHandler")
    public void onEvent(PersistenceEvent event, long sequence, boolean endOfBatch) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        log.debug("PersistenceEvent处理:{}(event(block({}),transactions({})),sequence({}),endOfBatch({}))",
                Thread.currentThread().getStackTrace()[1].getMethodName(),event.getBlock().getNum(),event.getTransactions().size(),sequence,endOfBatch);
        try {
            blockStage.add(event.getBlock());
            transactionStage.addAll(event.getTransactions());
            nodeOptStage.addAll(event.getNodeOpts());

            // 把区块的交易列表属性置为null,防止把交易信息存储到区块信息中
            event.getBlock().setTransactions(null);

            // 如区块暂存区的区块数量达不到批量入库大小,则返回
            if(blockStage.size()<batchSize) {
                maxBlockNumber=event.getBlock().getNum();
                return;
            }

            // 入库ES 入库节点操作记录到ES
            esImportService.batchImport(blockStage,transactionStage,nodeOptStage);
            // 入库Redis 更新Redis中的统计记录
            Set<NetworkStat> statistics = new HashSet<>();
            statistics.add(networkStatCache.getNetworkStat());
            redisImportService.batchImport(blockStage,transactionStage,statistics);
            blockStage.clear();
            transactionStage.clear();
            nodeOptStage.clear();

            // 查询序号最大的一条交易备份记录, 通知备份数据删除任务删除记录
            Long txMaxId = 0L;
            List<Transaction> transactions = event.getTransactions();
            if(!transactions.isEmpty()) txMaxId=transactions.get(0).getId();
            BakDataDeleteUtil.updateTxBakMaxId(txMaxId);

            // 查询序号最大的一条操作记录, 通知日志备份数据删除任务删除记录
            List<NodeOpt> nOptBaks = event.getNodeOpts();
            Long nOptMaxId = 0L;
            if(!nOptBaks.isEmpty()) nOptMaxId=nOptBaks.get(0).getId();
            BakDataDeleteUtil.updateNOptBakMaxId(nOptMaxId);

            maxBlockNumber=event.getBlock().getNum();
        }catch (Exception e){
            log.error("",e);
            throw e;
        }

        log.debug("处理耗时:{} ms",System.currentTimeMillis()-startTime);
    }
}