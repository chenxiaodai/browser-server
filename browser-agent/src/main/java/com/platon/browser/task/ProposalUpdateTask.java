package com.platon.browser.task;

import com.alibaba.fastjson.JSON;
import com.platon.browser.client.PlatonClient;
import com.platon.browser.client.ProposalParticiantStat;
import com.platon.browser.client.SpecialContractApi;
import com.platon.browser.dao.entity.NodeOpt;
import com.platon.browser.dao.entity.NodeOptExample;
import com.platon.browser.dao.entity.Proposal;
import com.platon.browser.dao.mapper.NodeOptMapper;
import com.platon.browser.dto.CustomBlock;
import com.platon.browser.dto.CustomNodeOpt;
import com.platon.browser.dto.CustomProposal;
import com.platon.browser.dto.ProposalMarkDownDto;
import com.platon.browser.engine.BlockChain;
import com.platon.browser.exception.BlockChainException;
import com.platon.browser.exception.BusinessException;
import com.platon.browser.exception.NoSuchBeanException;
import com.platon.browser.util.MarkDownParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.web3j.platon.BaseResponse;
import org.web3j.platon.bean.TallyResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.platon.browser.engine.util.CacheTool.PROPOSALS_CACHE;
import static com.platon.browser.engine.util.CacheTool.STAGE_DATA;

/**
 * @Auther: dongqile
 * @Date: 2019/8/17 20:09
 * @Description: 提案信息更新任务
 */
@Component
public class ProposalUpdateTask {
    private static Logger logger = LoggerFactory.getLogger(ProposalUpdateTask.class);
    @Autowired
    private PlatonClient client;
    public static final String QUERY_FLAG = "inquiry";
    @Autowired
    private BlockChain bc;
    @Autowired
    private SpecialContractApi sca;
    @Autowired
    private NodeOptMapper nodeOptMapper;

    /**
     * 同步任务功能说明：
     * a.http请求查询github治理提案的相关信息并补充
     * b.根据platon底层rpc接口查询提案结果
     */
    @Scheduled(cron = "0/3 * * * * ?")
    private void cron () {
        start();
    }

    public void start () {
        //获取全量数据
        Collection<CustomProposal> proposals = getAllProposal();
        //记录全量缓存中所有的proposalhash，用于后期补充操作记录的查询条件
        List <String> proposalList = new ArrayList <>();
        if (proposals.isEmpty()) return;
        CustomBlock curBlock = bc.getCurBlock();
        for (CustomProposal proposal : proposals) {//如果已经补充则无需补充
            proposalList.add(proposal.getHash());
            try {
                ProposalMarkDownDto proposalMarkDownDto = getMarkdownInfo(proposal.getUrl());
                proposal.updateWithProposalMarkDown(proposalMarkDownDto);
                if (CustomProposal.TypeEnum.CANCEL.code.equals(proposal.getType())) {
                    proposal.updateWithProposalMarkDown(proposalMarkDownDto);
                    //若是取消提案，则需要补充被取消提案相关信息
/*                    String cancelProposalString = MarkDownParserUtil.parserMD(proposalCache.getProposal(proposal.getHash()).getUrl());
                    ProposalMarkDownDto cancelProp = JSON.parseObject(cancelProposalString, ProposalMarkDownDto.class);*/
                    CustomProposal cp = getProposal(proposal.getCanceledPipId());
                    proposal.setCanceledTopic(cp.getTopic());
                }
                // 添加至全量缓存
                PROPOSALS_CACHE.addProposal(proposal);
                // 暂存至待入库列表
                STAGE_DATA.getProposalStage().updateProposal(proposal);
            } catch (NoSuchBeanException | BusinessException e) {
                logger.error("更新提案({})的主题和描述出错:{}", proposal.getPipId(), e.getMessage());
            } catch (IOException e) {
                logger.error("更新提案({})的主题和描述出错:获取不到{}", proposal.getPipId(), proposal.getUrl());
            }
            //需要更新的提案结果，查询类型1.投票中 2.预升级
            if (proposal.getStatus().equals(CustomProposal.StatusEnum.VOTING.code)
                    || proposal.getStatus().equals(CustomProposal.StatusEnum.PRE_UPGRADE.code)
                    || proposal.getStatus().equals(CustomProposal.StatusEnum.PASS.code)) {
                //发送rpc请求查询提案结果
                try {
                    ProposalParticiantStat pps = getProposalParticiantStat(proposal.getHash(),curBlock.getHash());
                    //设置参与人数
                    proposal.setAccuVerifiers(pps.getVoterCount());
                    //设置赞成票
                    proposal.setYeas(pps.getSupportCount());
                    //设置反对票
                    proposal.setNays(pps.getOpposeCount());
                    //设置弃权票
                    proposal.setAbstentions(pps.getAbstainCount());
                    //只有在结束快高之后才有返回提案结果
                    if (curBlock.getBlockNumber().longValue() >= Long.parseLong(proposal.getEndVotingBlock())) {
                        //设置状态
                        proposal.setStatus(getTallyResult(proposal.getHash()).getStatus());
                    }
                    // 添加至全量缓存
                    PROPOSALS_CACHE.addProposal(proposal);
                    // 暂存至待入库列表
                    STAGE_DATA.getProposalStage().updateProposal(proposal);
                } catch (Exception e) {
                    logger.error("更新提案({})的结果出错:{}", proposal.getPipId(), e.getMessage());

                }
            }
        }
        updateNodeOptInfo(proposalList);
    }

    private void updateNodeOptInfo ( List <String> proposalList ) {
        //补充操作记录中具体提案描述
        if (!proposalList.isEmpty()) {
            NodeOptExample nodeOptExample = new NodeOptExample();
            nodeOptExample.createCriteria().andTxHashIn(proposalList);
            List <NodeOpt> nodeOpts = nodeOptMapper.selectByExample(nodeOptExample);
            nodeOpts.forEach(nodeOpt -> {
                Proposal proposal = new Proposal();
                try {
                    proposal = PROPOSALS_CACHE.getProposal(nodeOpt.getTxHash());
                } catch (NoSuchBeanException e) {
                    logger.error("更新操作({})的结果出错:{}", proposal.getHash(), e.getMessage());
                }
                if (proposal != null) {
                    String desc = CustomNodeOpt.TypeEnum.PROPOSALS.tpl
                            .replace("ID", proposal.getPipId().toString())
                            .replace("TITLE", proposal.getTopic())
                            .replace("TYPE", CustomProposal.TypeEnum.TEXT.code);
                    nodeOpt.setDesc(desc);
                }
                CustomNodeOpt customNodeOpt = new CustomNodeOpt();
                BeanUtils.copyProperties(nodeOpt, customNodeOpt);
                STAGE_DATA.getStakingStage().updateNodeOpt(customNodeOpt);
            });
        }
    }

    /**
     * 获取所有提案
     * @return
     */
    public Collection<CustomProposal> getAllProposal(){
        return PROPOSALS_CACHE.getAllProposal();
    }

    /**
     * 根据hash取提案
     * @param hash
     * @return
     * @throws NoSuchBeanException
     */
    public CustomProposal getProposal(String hash) throws NoSuchBeanException {
        return PROPOSALS_CACHE.getProposal(hash);
    }

    /**
     * 取提案参与统计信息
     * @param proposalHash
     * @param blockHash
     * @return
     * @throws Exception
     */
    public ProposalParticiantStat getProposalParticiantStat(String proposalHash,String blockHash) throws Exception {
        return sca.getProposalParticipants(client.getWeb3j(), proposalHash, blockHash);
    }

    /**
     * 根据提案hash取提案投票结果
     * @param proposalHash
     * @return
     * @throws Exception
     */
    public TallyResult getTallyResult(String proposalHash) throws Exception {
        BaseResponse <TallyResult> result = client.getProposalContract().getTallyResult(proposalHash).send();
        if(result.isStatusOk()){
            return result.data;
        }
        throw new BlockChainException("查询不到提案[proposalHash="+proposalHash+"]对应的投票结果!");
    }

    /**
     * 根据URL获取markdown信息
     * @param url
     * @return
     * @throws IOException
     * @throws BusinessException
     */
    public ProposalMarkDownDto getMarkdownInfo(String url) throws IOException, BusinessException {
        String fileUrl = MarkDownParserUtil.acquireMD(url);
        if (fileUrl == null) throw new BusinessException("获取不到" + url);
        String proposalMarkString = MarkDownParserUtil.parserMD(fileUrl);
        return JSON.parseObject(proposalMarkString, ProposalMarkDownDto.class);
    }

}
