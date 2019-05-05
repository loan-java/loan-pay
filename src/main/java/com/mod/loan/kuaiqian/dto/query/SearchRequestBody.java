package com.mod.loan.kuaiqian.dto.query;

import com.mod.loan.kuaiqian.dto.common.SealDataType;

import javax.xml.bind.annotation.*;

/**
 * 报文实体
 *
 * @author zan.liang
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@XmlType(name = "searchRequestBody", propOrder = {"sealDataType"})
public class SearchRequestBody {


    @XmlElement(name = "sealDataType")
    private SealDataType sealDataType;

    public SealDataType getSealDataType() {
        return sealDataType;
    }

    public void setSealDataType(SealDataType sealDataType) {
        this.sealDataType = sealDataType;
    }


}
