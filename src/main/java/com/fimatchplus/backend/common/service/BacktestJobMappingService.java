package com.fimatchplus.backend.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 백테스트 엔진 jobId와 백테스트 ID 매핑을 Redis로 관리하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestJobMappingService {
    
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "backtest:job:";
    private static final Duration TTL = Duration.ofHours(24);
    
    /**
     * jobId와 backtestId 매핑 저장
     */
    public void saveMapping(String jobId, Long backtestId) {
        String key = KEY_PREFIX + jobId;
        redisTemplate.opsForValue().set(key, backtestId.toString(), TTL);
        log.info("Saved backtest job mapping: jobId={}, backtestId={}", jobId, backtestId);
    }
    
    /**
     * jobId로 backtestId 조회 후 매핑 삭제
     */
    public Long getAndRemoveMapping(String jobId) {
        String key = KEY_PREFIX + jobId;
        String backtestIdStr = redisTemplate.opsForValue().getAndDelete(key);
        
        if (backtestIdStr == null) {
            log.warn("No mapping found for jobId: {}", jobId);
            return null;
        }
        
        Long backtestId = Long.valueOf(backtestIdStr);
        log.info("Retrieved and removed backtest job mapping: jobId={}, backtestId={}", jobId, backtestId);
        return backtestId;
    }
    
    /**
     * 특정 jobId 매핑 존재 여부 확인
     */
    public boolean hasMapping(String jobId) {
        String key = KEY_PREFIX + jobId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * 매핑 수동 삭제 (필요한 경우)
     */
    public void removeMapping(String jobId) {
        String key = KEY_PREFIX + jobId;
        redisTemplate.delete(key);
        log.info("Removed backtest job mapping: jobId={}", jobId);
    }
}
