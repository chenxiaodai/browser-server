package com.platon.browser.agent.message;

import com.alibaba.fastjson.JSON;
import com.platon.browser.common.dto.agent.BlockDto;
import com.platon.browser.common.dto.agent.PendingTransactionDto;
import com.platon.browser.common.dto.agent.TransactionDto;
import com.platon.browser.common.dto.mq.Message;
import com.platon.browser.common.enums.MqMessageTypeEnum;
import com.platon.browser.dao.entity.Block;
import com.platon.browser.dao.entity.PendingTx;
import com.platon.browser.dao.entity.TransactionWithBLOBs;
import com.platon.browser.dao.mapper.BlockMapper;
import com.platon.browser.dao.mapper.PendingTxMapper;
import com.platon.browser.dao.mapper.TransactionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * User: dongqile
 * Date: 2018/10/30
 * Time: 18:28
 */
@Component
public class DBStorageService {
    private final Logger logger = LoggerFactory.getLogger(DBStorageService.class);

    @Autowired
    private BlockMapper blockMapper;

    @Autowired
    private TransactionMapper transactionMapper;

    @Autowired
    private PendingTxMapper pendingTxMapper;

    @RabbitListener(queues = "#{platonQueue.name}")
    public void receive ( String msg ) {
        logger.debug(msg);
        Message message = JSON.parseObject(msg, Message.class);
        switch (MqMessageTypeEnum.valueOf(message.getType().toUpperCase())) {
            case BLOCK:
                logger.debug("STOMP区块信息入库: {}", msg);
                BlockDto blockDto = JSON.parseObject(message.getStruct(), BlockDto.class);
                //构建dto结构，转存数据库结构
                //区块相关block
                Block block = bulidBlock(blockDto, message);
                try {
                    blockMapper.insertSelective(block);
                    logger.debug("block data insert...");
                } catch (Exception e) {
                    Block block1 = blockMapper.selectByPrimaryKey(block.getHash());
                    if (!ObjectUtils.isEmpty(block1)) {
                        blockMapper.updateByPrimaryKeySelective(block);
                        logger.debug("block data repeat...,update data", e.getMessage());

                    }
                }

                //交易相关transaction
                if (blockDto.getTransaction().size() > 0) {
                    List <TransactionWithBLOBs> transactionList = buildTransaction(blockDto, message);
                    try {
                        transactionMapper.batchInsert(transactionList);
                        logger.debug("transaction data insert...");
                    } catch (Exception e) {
                        for (TransactionWithBLOBs transactionWithBLOBs : transactionList) {
                            TransactionWithBLOBs transaction = transactionMapper.selectByPrimaryKey(transactionWithBLOBs.getHash());
                            if (!ObjectUtils.isEmpty(transaction)) {
                                transactionMapper.updateByPrimaryKeySelective(transaction);
                                logger.debug("transaction data repeat...,update data", e.getMessage());
                            }
                        }
                    }
                }
                break;

            case PENDING:
                logger.debug("STOMP挂起交易信息入库: {}", msg);
                //获取信息中pending交易列表
                List <PendingTransactionDto> list = JSON.parseArray(message.getStruct(), PendingTransactionDto.class);
                List <PendingTx> pendingTxes = buidPendingTx(list, message);
                try {
                    pendingTxMapper.batchInsert(pendingTxes);
                    logger.debug("pendingtransaction data insert...");
                } catch (Exception e) {
                    for (PendingTx pendingTx : pendingTxes) {
                        PendingTx pending = pendingTxMapper.selectByPrimaryKey(pendingTx.getHash());
                        if (!ObjectUtils.isEmpty(pending)) {
                            pendingTxMapper.updateByPrimaryKeySelective(pendingTx);
                            logger.debug("pendingtransaction data repeat...,update data", e.getMessage());
                        }
                    }
                }
                break;
        }
    }

    private Block bulidBlock ( BlockDto blockDto, Message message ) {
        Block block = new Block();
        try {
            BeanUtils.copyProperties(blockDto, block);
            block.setNumber(Long.valueOf(blockDto.getNumber()));
            if (blockDto.getTimestamp() == 0) {
                block.setTimestamp(new Date(3600));
            } else {
                block.setTimestamp(new Date(blockDto.getTimestamp() * 1000l));
            }
            block.setSize((int) blockDto.getSize());//考虑转换格式类型，高精度转低精度可能会导致数据失准
            block.setChainId(message.getChainId());
            block.setEnergonAverage(blockDto.getEnergonAverage().toString());
            block.setEnergonLimit(blockDto.getEnergonLimit().toString());
            block.setEnergonUsed(blockDto.getEnergonUsed().toString());
            block.setTransactionNumber(blockDto.getTransactionNumber());
            block.setCreateTime(new Date());
            block.setUpdateTime(new Date());
        } catch (Exception e) {
            logger.error("数据转化异常", e.getMessage());
        }
        return block;
    }

    private List <TransactionWithBLOBs> buildTransaction ( BlockDto blockDto, Message message ) {
        List <TransactionWithBLOBs> transactionList = new ArrayList <>();
        List <TransactionDto> transactionDtos = blockDto.getTransaction();
        for (TransactionDto transactionDto : transactionDtos) {
            TransactionWithBLOBs transaction = new TransactionWithBLOBs();
            BeanUtils.copyProperties(transactionDto, transaction);
            transaction.setActualTxCost(transactionDto.getActualTxCoast().toString());
            transaction.setBlockNumber(Long.valueOf(transactionDto.getBlockNumber().toString()));
            transaction.setChainId(message.getChainId());
            transaction.setCreateTime(new Date());
            transaction.setUpdateTime(new Date());
            transaction.setEnergonLimit(transactionDto.getEnergonLimit().toString());
            transaction.setEnergonPrice(transactionDto.getEnergonPrice().toString());
            transaction.setEnergonUsed(transactionDto.getEnergonUsed().toString());
            transaction.setTxReceiptStatus(Integer.parseInt(transactionDto.getTxReceiptStatus().substring(2), 16));
            transaction.setTransactionIndex(transactionDto.getTransactionIndex().intValue());
            transaction.setReceiveType(transactionDto.getReceiveType());
            transaction.setInput(transactionDto.getInput() != null ? transactionDto.getInput() : "0x");
            transactionList.add(transaction);
        }
        return transactionList;
    }

    private List <PendingTx> buidPendingTx ( List <PendingTransactionDto> list, Message message ) {
        List <PendingTx> pendingTxes = new ArrayList <>();
        if (list.size() > 0) {
            for (PendingTransactionDto pendingTransactionDto : list) {
                PendingTx pendingTx = new PendingTx();
                BeanUtils.copyProperties(pendingTransactionDto, pendingTx);
                pendingTx.setUpdateTime(new Date());
                pendingTx.setCreateTime(new Date());
                pendingTx.setEnergonLimit(pendingTransactionDto.getEnergonLimit().toString());
                pendingTx.setEnergonPrice(pendingTransactionDto.getEnergonPrice().toString());
                pendingTx.setEnergonUsed("pending");
                pendingTx.setTimestamp(new Date(pendingTransactionDto.getTimestamp()));
                pendingTx.setChainId(message.getChainId());
                pendingTxes.add(pendingTx);
            }
        }
        return pendingTxes;
    }

}