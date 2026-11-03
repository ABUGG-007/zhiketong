package com.zhiketong.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在线接口文档配置（springdoc + Swagger UI）。
 *
 * <p>启动后访问：http://localhost:8080/swagger-ui.html</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zhiketongOpenApi() {
        return new OpenAPI().info(new Info()
                .title("智客通 · 智能对话客服与工单管理系统 API")
                .description("面向 B 端企业的商业化智能客服 SaaS，提供会话、知识库、工单、坐席与网页接入开放能力")
                .version("1.0.0")
                .contact(new Contact().name("智客通开发组")));
    }
}
