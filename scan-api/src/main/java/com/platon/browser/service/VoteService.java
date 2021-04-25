package com.platon.browser.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platon.browser.dao.entity.Vote;
import com.platon.browser.dao.entity.VoteExample;
import com.platon.browser.dao.mapper.VoteMapper;
import com.platon.browser.request.proposal.VoteListRequest;
import com.platon.browser.response.RespPage;
import com.platon.browser.response.proposal.VoteListResp;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: 王章雄
 * Email:wangzhangxiong@juzix.net
 * Date: 2019/8/20
 * Time: 20:40
 * Desc:
 */
@Service
public class VoteService {

    @Resource
    private VoteMapper voteMapper;

    public RespPage<VoteListResp> queryByProposal(VoteListRequest voteListRequest) {
        Page<Vote> page = new Page<>(voteListRequest.getPageNo(), voteListRequest.getPageSize());
        /**
         * 根据时间戳倒序查询hash值
         */
        VoteExample voteExample = new VoteExample();
        voteExample.setOrderByClause(" timestamp desc");
        VoteExample.Criteria criteria = voteExample.createCriteria();
        criteria.andProposalHashEqualTo(voteListRequest.getProposalHash());
        if (StringUtils.isNotBlank(voteListRequest.getOption())) {
            criteria.andOptionEqualTo(Integer.parseInt(voteListRequest.getOption()));
        }
        /** 分页根据提案hash查询投票列表 */
        IPage<Vote> votes = voteMapper.selectByExample(page, voteExample);
        RespPage<VoteListResp> respPage = new RespPage<>();
        if (!CollectionUtils.isEmpty(votes.getRecords())) {
            List<VoteListResp> voteListResps = new ArrayList<>(votes.getRecords().size());
            votes.getRecords().forEach(vote -> {
                /**
                 * 循环匹配数据
                 */
                VoteListResp resp = new VoteListResp();
                BeanUtils.copyProperties(vote, resp);
                resp.setVoter(vote.getNodeId());
                resp.setVoterName(vote.getNodeName());
                resp.setTxHash(vote.getHash());
                resp.setTimestamp(vote.getTimestamp().getTime());
                resp.setOption(String.valueOf(vote.getOption()));
                voteListResps.add(resp);
            });
            respPage.init(voteListResps, page.getTotal(), page.getTotal(), page.getPages());
        }
        return respPage;
    }
}
