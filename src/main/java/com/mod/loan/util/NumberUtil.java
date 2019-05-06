package com.mod.loan.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * @ author liujianjian
 * @ date 2019/5/6 13:23
 */
public class NumberUtil {

    /**
     * 元转分，确保price保留两位有效数字
     */
    public static int yuan2fen(double price) {
        DecimalFormat df = new DecimalFormat("#.00");
        price = Double.valueOf(df.format(price));
        int money = (int)(price * 100);
        return money;
    }

    /**
     * 分转元，转换为bigDecimal在toString
     */
    public static String fen2yuan(long price) {
        return BigDecimal.valueOf(price).divide(new BigDecimal(100)).toString();
    }
}
