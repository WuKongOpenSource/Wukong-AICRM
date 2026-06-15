package com.kakarote.ai_crm.controller;

import com.kakarote.ai_crm.common.BasePage;
import com.kakarote.ai_crm.common.result.Result;
import com.kakarote.ai_crm.entity.BO.AddressBookQueryBO;
import com.kakarote.ai_crm.entity.VO.AddressBookDetailVO;
import com.kakarote.ai_crm.entity.VO.AddressBookEmployeeVO;
import com.kakarote.ai_crm.service.IAddressBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/addressBook")
@RequiredArgsConstructor
@Tag(name = "通讯录")
public class AddressBookController {

    private final IAddressBookService addressBookService;

    @PostMapping("/queryPageList")
    @Operation(summary = "查询通讯录员工")
    public Result<BasePage<AddressBookEmployeeVO>> queryPageList(@RequestBody AddressBookQueryBO queryBO) {
        return Result.ok(addressBookService.queryPageList(queryBO));
    }

    @GetMapping("/detail/{userId}")
    @Operation(summary = "查询通讯录员工详情")
    public Result<AddressBookDetailVO> detail(@PathVariable("userId") Long userId) {
        return Result.ok(addressBookService.getDetail(userId));
    }
}
