package com.speednet.module.infra.framework.file.config;

import com.speednet.module.infra.framework.file.core.client.FileClientFactory;
import com.speednet.module.infra.framework.file.core.client.FileClientFactoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件配置类
 *
 * @author SpeedNet
 */
@Configuration(proxyBeanMethods = false)
public class SpeednetFileAutoConfiguration {

    @Bean
    public FileClientFactory fileClientFactory() {
        return new FileClientFactoryImpl();
    }

}
