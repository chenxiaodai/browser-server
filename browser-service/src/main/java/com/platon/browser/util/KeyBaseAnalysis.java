package com.platon.browser.util;

import com.platon.browser.dto.keybase.Completion;
import com.platon.browser.dto.keybase.Components;
import com.platon.browser.dto.keybase.KeyBaseUser;
import com.platon.browser.dto.keybase.ValueScore;

import java.util.List;

/**
 * @Auther: dongqile
 * @Date: 2019/9/28
 * @Description: keyBase解析工具
 */
public class KeyBaseAnalysis {

    private KeyBaseAnalysis(){}

    public static String getKeyBaseUseName(KeyBaseUser keyBaseUser){
        List <Completion> completions = keyBaseUser.getCompletions();
        if (completions == null || completions.isEmpty()) return null;
        // 取最新一条
        Completion completion = completions.get(0);
        Components components = completion.getComponents();
        ValueScore vs = components.getUsername();
        if(vs==null) return null;
        return vs.getVal();
    }


    public static String getKeyBaseIcon( KeyBaseUser keyBaseUser){
        List <Completion> completions = keyBaseUser.getCompletions();
        if (completions == null || completions.isEmpty()) return null;
        // 取最新一条
        Completion completion = completions.get(0);
        //获取头像
        return completion.getThumbnail();
    }
}