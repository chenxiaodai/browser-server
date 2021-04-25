package com.platon.browser.dao.mapper;

import com.platon.browser.dao.entity.TokenHolder;
import com.platon.browser.dao.entity.TokenHolderExample;
import com.platon.browser.dao.entity.TokenHolderKey;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TokenHolderMapper {
    long countByExample(TokenHolderExample example);

    int deleteByExample(TokenHolderExample example);

    int deleteByPrimaryKey(TokenHolderKey key);

    int insert(TokenHolder record);

    int insertSelective(TokenHolder record);

    List<TokenHolder> selectByExample(TokenHolderExample example);

    TokenHolder selectByPrimaryKey(TokenHolderKey key);

    int updateByExampleSelective(@Param("record") TokenHolder record, @Param("example") TokenHolderExample example);

    int updateByExample(@Param("record") TokenHolder record, @Param("example") TokenHolderExample example);

    int updateByPrimaryKeySelective(TokenHolder record);

    int updateByPrimaryKey(TokenHolder record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table token_holder
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsert(@Param("list") List<TokenHolder> list);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table token_holder
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsertSelective(@Param("list") List<TokenHolder> list, @Param("selective") TokenHolder.Column ... selective);
}