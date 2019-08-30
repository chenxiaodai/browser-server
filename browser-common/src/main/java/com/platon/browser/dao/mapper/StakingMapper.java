package com.platon.browser.dao.mapper;

import com.platon.browser.dao.entity.Staking;
import com.platon.browser.dao.entity.StakingExample;
import com.platon.browser.dao.entity.StakingKey;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface StakingMapper {
    long countByExample(StakingExample example);

    int deleteByExample(StakingExample example);

    int deleteByPrimaryKey(StakingKey key);

    int insert(Staking record);

    int insertSelective(Staking record);

    List<Staking> selectByExampleWithBLOBs(StakingExample example);

    List<Staking> selectByExample(StakingExample example);

    Staking selectByPrimaryKey(StakingKey key);

    int updateByExampleSelective(@Param("record") Staking record, @Param("example") StakingExample example);

    int updateByExampleWithBLOBs(@Param("record") Staking record, @Param("example") StakingExample example);

    int updateByExample(@Param("record") Staking record, @Param("example") StakingExample example);

    int updateByPrimaryKeySelective(Staking record);

    int updateByPrimaryKeyWithBLOBs(Staking record);

    int updateByPrimaryKey(Staking record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table staking
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsert(@Param("list") List<Staking> list);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table staking
     *
     * @mbg.generated
     * @project https://github.com/itfsw/mybatis-generator-plugin
     */
    int batchInsertSelective(@Param("list") List<Staking> list, @Param("selective") Staking.Column ... selective);
}