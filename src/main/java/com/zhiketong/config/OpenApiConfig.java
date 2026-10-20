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
                .description("JavaEE 期末大作业后端接口文档")
                .version("1.0.0")
                .contact(new Contact().name("智客通开发组")));
    }
}
