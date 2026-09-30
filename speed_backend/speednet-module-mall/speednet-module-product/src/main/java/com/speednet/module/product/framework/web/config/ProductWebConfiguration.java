package com.speednet.module.product.framework.web.config;

import com.speednet.framework.swagger.config.SpeednetSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * product 模块的 web 组件的 Configuration
 *
 * @author SpeedNet
 */
@Configuration(proxyBeanMethods = false)
public class ProductWebConfiguration {

    /**
     * product 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi productGroupedOpenApi() {
        return SpeednetSwaggerAutoConfiguration.buildGroupedOpenApi("product");
    }

}
