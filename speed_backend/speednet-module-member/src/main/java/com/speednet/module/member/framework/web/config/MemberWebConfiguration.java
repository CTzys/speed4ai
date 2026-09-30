package com.speednet.module.member.framework.web.config;

import com.speednet.framework.swagger.config.SpeednetSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * member 模块的 web 组件的 Configuration
 *
 * @author SpeedNet
 */
@Configuration(proxyBeanMethods = false)
public class MemberWebConfiguration {

    /**
     * member 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi memberGroupedOpenApi() {
        return SpeednetSwaggerAutoConfiguration.buildGroupedOpenApi("member");
    }

}
