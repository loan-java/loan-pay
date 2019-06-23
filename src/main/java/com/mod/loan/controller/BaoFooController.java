package com.mod.loan.controller;

import com.mod.loan.service.BaoFooService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author yutian
 */
@Slf4j
@RequestMapping("/baofoo")
@RestController
public class BaoFooController {

    @Autowired
    private BaoFooService baoFooService;

    @GetMapping("/bindQuery")
    public String baofooBindQuery(String key) {
        if ("baofoo!@#".equals(key)) {
            baoFooService.bindQuery();
            return "success";
        }
        return "key错误";
    }
}
