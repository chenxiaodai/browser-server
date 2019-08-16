package com.platon.browser.engine;

import com.platon.browser.dao.entity.Address;
import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: dongqile
 * Date: 2019/8/14
 * Time: 16:46
 */
@Data
public class AddressExecuteResult {
    private Map <String, Address> addressMap = new HashMap <>();
    private Set<Address> addAddress =  new HashSet<>();
    private Set<Address> updateAddress = new HashSet <>();
}