package com.mod.loan.service;

import com.mod.loan.common.mapper.BaseService;
import com.mod.loan.model.NotifyInfo;
import com.mod.loan.model.User;

import javax.servlet.http.HttpServletRequest;

public interface NotifyInfoService extends BaseService<NotifyInfo, Long> {

    String poPayNotifyCheck(HttpServletRequest httpRequest);

    String cnpPayNotifyCheck(HttpServletRequest httpRequest);

}
