package com.platon.browser.job;

import com.platon.browser.client.PlatonClient;
import com.platon.browser.dao.entity.PendingTx;
import com.platon.browser.dao.entity.PendingTxExample;
import com.platon.browser.dao.entity.Transaction;
import com.platon.browser.dao.entity.TransactionExample;
import com.platon.browser.dao.mapper.PendingTxMapper;
import com.platon.browser.dao.mapper.TransactionMapper;
import com.platon.browser.enums.ErrorCodeEnum;
import com.platon.browser.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class PendingUpdateJob {

     /** 挂起交易更新任务
     * <p>
     * <p>
     * 1. 定时查询挂起交易列表
     * 2. 比对交易列表数据
     * 3. 相同表示挂起交易已出块，删除挂起交易列表中该数据*/
    private static Logger logger = LoggerFactory.getLogger(PendingUpdateJob.class);
    @Autowired
    private TransactionMapper transactionMapper;
    @Autowired
    private PendingTxMapper pendingTxMapper;
    @Autowired
    private PlatonClient platon;
    @Value("${platon.chain.active}")
    private String chainId;
    /**
     * 更新待处理交易
     */
    @Scheduled(cron="0/5 * * * * ?")
    protected void updatePending () {
        logger.debug("*** In the PendingUpdateJob *** ");
        try {
            //比对交易信息数据更新pending交易列表
            List <PendingTx> pendingTxeList = pendingTxMapper.selectByExample(new PendingTxExample());
            if (pendingTxeList.size() > 0) {
                for (PendingTx pendingTx : pendingTxeList) {
                    TransactionExample transactionExample = new TransactionExample();
                    transactionExample.createCriteria().andHashEqualTo(pendingTx.getHash()).andChainIdEqualTo(chainId);
                    List<Transaction> transactionList = transactionMapper.selectByExample(transactionExample);
                    if (transactionList.size() == 1) {
                        pendingTxMapper.deleteByPrimaryKey(pendingTx.getHash());
                        logger.debug("PendingTx update..... ->{" + pendingTx.getHash() + "}");
                    }
                    if (transactionList.size() > 1) {
                        logger.error("PendingTx comparison transaction repeat!!!... TxHash:{" + pendingTx.getHash() + "}");
                        throw new AppException(ErrorCodeEnum.PENDINGTX_REPEAT);
                    }
                    logger.debug("PendingTx update list is null...,wait next time update...");
                }
            }

        } catch (Exception e) {
            logger.error("PendingUpdateJob Exception:{}", e.getMessage());
        }
        logger.debug("*** End the PendingUpdateJob *** ");
    }


}