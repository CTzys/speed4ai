package com.speednet.framework.banner.core;

import cn.hutool.core.thread.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.ClassUtils;

import java.util.concurrent.TimeUnit;

/**
 * 项目启动成功后，提供文档相关的地址
 *
 * @author SpeedNet
 */
@Slf4j
public class BannerApplicationRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        ThreadUtil.execute(() -> {
            ThreadUtil.sleep(1, TimeUnit.SECONDS); // 延迟 1 秒，保证输出到结尾
            log.info("\n----------------------------------------------------------\n\t" +
                            "项目启动成功！\n\t" +
                            "接口文档: \t{} \n\t" +
                            "开发文档: \t{} \n\t" +
                            "视频教程: \t{} \n" +
                            "----------------------------------------------------------",
                    "https://doc.speednet.local/api-doc/",
                    "https://doc.speednet.local",
                    "https://t.zsxq.com/02Yf6M7Qn");

            // 数据报表
            if (isNotPresent("com.speednet.module.report.framework.security.config.SecurityConfiguration")) {
                System.out.println("[报表模块 speednet-module-report - 已禁用][参考 https://doc.speednet.local/report/ 开启]");
            }
            // 工作流
            if (isNotPresent("com.speednet.module.bpm.framework.flowable.config.BpmFlowableConfiguration")) {
                System.out.println("[工作流模块 speednet-module-bpm - 已禁用][参考 https://doc.speednet.local/bpm/ 开启]");
            }
            // 商城系统
            if (isNotPresent("com.speednet.module.trade.framework.web.config.TradeWebConfiguration")) {
                System.out.println("[商城系统 speednet-module-mall - 已禁用][参考 https://doc.speednet.local/mall/build/ 开启]");
            }
            // 微信公众号
            if (isNotPresent("com.speednet.module.mp.framework.mp.config.MpConfiguration")) {
                System.out.println("[微信公众号 speednet-module-mp - 已禁用][参考 https://doc.speednet.local/mp/build/ 开启]");
            }
            // 支付平台
            if (isNotPresent("com.speednet.module.pay.framework.pay.config.PayConfiguration")) {
                System.out.println("[支付系统 speednet-module-pay - 已禁用][参考 https://doc.speednet.local/pay/build/ 开启]");
            }
            // AI 大模型
            if (isNotPresent("com.speednet.module.ai.framework.web.config.AiWebConfiguration")) {
                System.out.println("[AI 大模型 speednet-module-ai - 已禁用][参考 https://doc.speednet.local/ai/build/ 开启]");
            }
            // IoT 物联网
            if (isNotPresent("com.speednet.module.iot.framework.web.config.IotWebConfiguration")) {
                System.out.println("[IoT 物联网 speednet-module-iot - 已禁用][参考 https://doc.speednet.local/iot/build/ 开启]");
            }
            // IM 即时通讯
            if (isNotPresent("com.speednet.module.im.framework.web.config.ImWebConfiguration")) {
                System.out.println("[IM 即时通讯 speednet-module-im - 已禁用][参考 https://doc.speednet.local/im/build/ 开启]");
            }
        });
    }

    private static boolean isNotPresent(String className) {
        return !ClassUtils.isPresent(className, ClassUtils.getDefaultClassLoader());
    }

}
