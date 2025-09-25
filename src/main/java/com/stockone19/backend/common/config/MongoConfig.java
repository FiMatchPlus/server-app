package com.stockone19.backend.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * MongoDB 트랜잭션 설정
 */
@Configuration
public class MongoConfig {

    @Bean(name = "transactionManager")
    public PlatformTransactionManager mongoTransactionManager(MongoDatabaseFactory mongoDatabaseFactory) {
        MongoTransactionManager transactionManager = new MongoTransactionManager(mongoDatabaseFactory);
        // MongoDB 트랜잭션 타임아웃 설정 (5분)
        transactionManager.setDefaultTimeout(300);
        return transactionManager;
    }
}
