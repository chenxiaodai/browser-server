package com.platon.browser.common.complement.dto.slash;

import com.platon.browser.common.complement.dto.BusinessParam;
import com.platon.browser.common.enums.BusinessType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class SlashLowRate extends BusinessParam {
    @Override
    public BusinessType getBusinessType() {
        return BusinessType.SLASH_LOW_RATE;
    }
}