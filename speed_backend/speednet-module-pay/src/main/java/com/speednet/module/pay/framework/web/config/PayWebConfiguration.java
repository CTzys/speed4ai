package com.speednet.module.pay.framework.web.config;

import com.speednet.framework.swagger.config.SpeednetSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * pay 模块的 web 组件的 Configuration
 *
 * @author SpeedNet
 */
@Configuration(proxyBeanMethods = false)
public class PayWebConfiguration {

    /**
     * pay 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi payGroupedOpenApi() {
        return SpeednetSwaggerAutoConfiguration.buildGroupedOpenApi("pay");
    }

}
