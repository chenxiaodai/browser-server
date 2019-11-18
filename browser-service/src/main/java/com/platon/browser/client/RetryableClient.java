package com.platon.browser.client;

import com.platon.browser.enums.Web3jProtocolEnum;
import com.platon.browser.exception.ConfigLoadingException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.web3j.platon.contracts.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;

import java.math.BigInteger;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 链参数统一配置项
 * @Auther: Chendongming
 * @Date: 2019/11/18 10:12
 * @Description:
 */
@Slf4j
@Component
public class RetryableClient {
    private static final ReentrantReadWriteLock WEB3J_CONFIG_LOCK = new ReentrantReadWriteLock();
    @Value("${platon.web3j.protocol}")
    private Web3jProtocolEnum protocol;
    @Getter
    @Value("${platon.web3j.addresses}")
    private List<String> addresses;
    private List<Web3jWrapper> web3jWrappers=new ArrayList<>();
    private Web3jWrapper currentWeb3jWrapper;
    // 委托合约接口
    private DelegateContract delegateContract;
    public DelegateContract getDelegateContract(){return delegateContract;}
    // 节点合约接口
    private NodeContract nodeContract;
    public NodeContract getNodeContract(){return nodeContract;}
    // 提案合约接口
    private ProposalContract proposalContract;
    public ProposalContract getProposalContract(){return proposalContract;}
    // 锁仓合约接口
    private RestrictingPlanContract restrictingPlanContract;
    public RestrictingPlanContract getRestrictingPlanContract(){return restrictingPlanContract;}
    // 惩罚合约接口
    private SlashContract slashContract;
    public SlashContract getSlashContract(){return slashContract;}
    // 质押合约接口
    private StakingContract stakingContract;
    public StakingContract getStakingContract(){return stakingContract;}

    @Retryable(value = Exception.class, maxAttempts = Integer.MAX_VALUE)
    public void init() throws ConfigLoadingException {
        WEB3J_CONFIG_LOCK.writeLock().lock();
        try {
            web3jWrappers.clear();
            addresses.forEach(address->{
                Web3jService service=null;
                if(protocol==Web3jProtocolEnum.WS){
                    WebSocketService wss = new WebSocketService(protocol.getHead()+address,true);
                    try {
                        wss.connect();
                        service=wss;
                    } catch (ConnectException e) {
                        log.error("Websocket地址({})无法连通:",protocol.getHead()+address,e);
                        return;
                    }
                }else if(protocol==Web3jProtocolEnum.HTTP){
                    service = new HttpService(protocol.getHead()+address);
                }else{
                    log.error("Web3j连接协议[{}]不合法!",protocol.getHead());
                    System.exit(1);
                }
                Web3jWrapper web3j = Web3jWrapper.builder()
                        .address(protocol.getHead()+address)
                        .web3jService(service)
                        .web3j(Web3j.build(service))
                        .build();
                web3jWrappers.add(web3j);
            });
            if(web3jWrappers.isEmpty()) throw new ConfigLoadingException("没有可用Web3j实例!");
            updateCurrentWeb3jWrapper();
        }catch (Exception e){
            log.error("加载Web3j配置错误,将重试:", e);
            throw e;
        }finally {
            WEB3J_CONFIG_LOCK.writeLock().unlock();
        }
    }

    @Retryable(value = Exception.class, maxAttempts = Integer.MAX_VALUE)
    public Web3jWrapper getWeb3jWrapper(){
        WEB3J_CONFIG_LOCK.readLock().lock();
        try{
            return currentWeb3jWrapper;
        }catch (Exception e){
            log.error("加载Web3j配置错误:", e);
        }finally {
            WEB3J_CONFIG_LOCK.readLock().unlock();
        }
        return null;
    }

    @Retryable(value = Exception.class, maxAttempts = Integer.MAX_VALUE)
    private void updateContract(){
        delegateContract = DelegateContract.load(currentWeb3jWrapper.getWeb3j());
        nodeContract = NodeContract.load(currentWeb3jWrapper.getWeb3j());
        proposalContract = ProposalContract.load(currentWeb3jWrapper.getWeb3j());
        restrictingPlanContract = RestrictingPlanContract.load(currentWeb3jWrapper.getWeb3j());
        slashContract = SlashContract.load(currentWeb3jWrapper.getWeb3j());
        stakingContract = StakingContract.load(currentWeb3jWrapper.getWeb3j());
    }

    @Retryable(value = Exception.class, maxAttempts = Integer.MAX_VALUE)
    public void updateCurrentWeb3jWrapper() {
        WEB3J_CONFIG_LOCK.writeLock().lock();
        try {
            Web3jWrapper preWeb3j = currentWeb3jWrapper;
            // 检查所有Web3j的连通性, 取块高最高的作为当前web3j
            long maxBlockNumber = 0;
            for (Web3jWrapper wrapper:web3jWrappers) {
                try {
                    BigInteger blockNumber = wrapper.getWeb3j().platonBlockNumber().send().getBlockNumber();
                    if (blockNumber.longValue() > maxBlockNumber) {
                        maxBlockNumber = blockNumber.longValue();
                        currentWeb3jWrapper = wrapper;
                    }
                } catch (Exception e2) {
                    log.info("候选Web3j实例({})无效！",wrapper.getAddress());
                }
            }
            if(preWeb3j==null||preWeb3j!=currentWeb3jWrapper){
                // 前任web3j为空或Web3j有变动,则更新合约变量
                updateContract();
            }
            if(maxBlockNumber==0){
                log.info("当前所有候选Web3j实例均无法连通!");
                if(protocol==Web3jProtocolEnum.WS){
                    log.info("重新初始化websocket连接!");
                    try {
                        init();
                    } catch (ConfigLoadingException e) {
                        log.error("重新初始化失败!");
                    }
                }
            }
        }finally {
            WEB3J_CONFIG_LOCK.writeLock().unlock();
        }
    }
}