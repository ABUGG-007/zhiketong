package com.zhiketong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智客通 —— 智能对话客服与工单管理系统 启动类。
 *
 * @author 智客通开发组
 */
@SpringBootApplication
@MapperScan("com.zhiketong.mapper")
@EnableScheduling
@EnableAsync
public class ZhiketongApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhiketongApplication.class, args);
    }
}
