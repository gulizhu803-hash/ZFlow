package com.zgl.zflow;

import com.zgl.zflow.domain.util.redis.IRedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@Slf4j
public class ZFlowApplication {

    @Resource
    private IRedisUtil redisUtil;

    public static void main(String[] args) {
        SpringApplication.run(ZFlowApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        redisUtil.clear();
        log.info("=========== 初始化成功，开始运行 ZFlow ===========");
    }

}
