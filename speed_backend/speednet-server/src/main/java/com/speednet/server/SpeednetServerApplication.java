package com.speednet.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目的启动类
 *
 * 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
 * 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
 * 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
 *
 * @author SpeedNet
 */
@SuppressWarnings("SpringComponentScan") // 忽略 IDEA 无法识别 ${speednet.info.base-package}
@SpringBootApplication(scanBasePackages = {"${speednet.info.base-package}.server", "${speednet.info.base-package}.module"})
public class SpeednetServerApplication {

    public static void main(String[] args) {
        // 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章

        SpringApplication.run(SpeednetServerApplication.class, args);
//        new SpringApplicationBuilder(SpeednetServerApplication.class)
//                .applicationStartup(new BufferingApplicationStartup(20480))
//                .run(args);

        // 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
        // 如果你碰到启动的问题，请认真阅读 https://doc.speednet.local/quick-start/ 文章
    }

}
