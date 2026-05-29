package com.thirdeye30.resumehelper.resumemanager.services.impl;

import java.util.UUID;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;

import com.thirdeye30.resumehelper.resumemanager.externalcontollers.TokenManagerClient;
import com.thirdeye30.resumehelper.resumemanager.services.TokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenServiceImpl implements TokenService {
    
    private final StringRedisTemplate redisTemplate;
    private final TokenManagerClient tokenManager;
    
    @Value("${thirdeye.redis.balance-prefix}")
    private String redisBalancePrefix;
    
    @Override
    public Long getToken(UUID userId) {
        String tokenKey = redisBalancePrefix + userId.toString();
        String cachedTokens = redisTemplate.opsForValue().get(tokenKey);
        
        long balance;
        
        if (cachedTokens == null) {
            log.info("Cache miss for user {}. Fetching from TokenManager service.", userId);
            ResponseEntity<Long> response = tokenManager.getToken(userId);
            balance = (response.getBody() != null) ? response.getBody() : 0L;
            redisTemplate.opsForValue().set(tokenKey, String.valueOf(balance), Duration.ofMinutes(30));
        } else {
            balance = Long.parseLong(cachedTokens);
        }
        
        if (balance < 1) {
            log.warn("Action blocked: User {} has insufficient tokens ({})", userId, balance);
            throw new RuntimeException("Insufficient tokens to proceed");
        }
        return balance;
    }

    @Override
    @Async
    public void subtractToken(UUID userId, Long amount) {
        log.info("Requesting subtraction of {} tokens for user {}", amount, userId);
        tokenManager.subtractToken(userId, amount);
    }
}