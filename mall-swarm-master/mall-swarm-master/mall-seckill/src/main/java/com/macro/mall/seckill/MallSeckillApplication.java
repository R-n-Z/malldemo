package com.macro.mall.seckill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 秒杀服务启动类
 */
@SpringBootApplication
@EnableKafka
public class MallSeckillApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MallSeckillApplication.class, args);
    }
}