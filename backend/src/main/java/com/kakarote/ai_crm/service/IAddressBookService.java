package com.kakarote.ai_crm.service;

import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.entity.BO.AddressBookQueryBO;
import com.kakarote.ai_crm.entity.VO.AddressBookDetailVO;
import com.kakarote.ai_crm.entity.VO.AddressBookEmployeeVO;

public interface IAddressBookService {

    BasePage<AddressBookEmployeeVO> queryPageList(AddressBookQueryBO queryBO);

    AddressBookDetailVO getDetail(Long userId);
}
