package com.thefale.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> otpSendBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> otpVerifyBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> globalBuckets = new ConcurrentHashMap<>();

    public boolean tryConsumeOtpSend(String clientIp) {
        Bucket bucket = otpSendBuckets.computeIfAbsent(clientIp, k -> createOtpSendBucket());
        return bucket.tryConsume(1);
    }

    public boolean tryConsumeOtpVerify(String clientIp) {
        Bucket bucket = otpVerifyBuckets.computeIfAbsent(clientIp, k -> createOtpVerifyBucket());
        return bucket.tryConsume(1);
    }

    public boolean tryConsumeGlobal(String clientIp) {
        Bucket bucket = globalBuckets.computeIfAbsent(clientIp, k -> createGlobalBucket());
        return bucket.tryConsume(1);
    }

    public void clear() {
        otpSendBuckets.clear();
        otpVerifyBuckets.clear();
        globalBuckets.clear();
    }

    private Bucket createOtpSendBucket() {
        // 3 requests per 15 minutes
        Refill refill = Refill.intervally(3, Duration.ofMinutes(15));
        Bandwidth limit = Bandwidth.classic(3, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createOtpVerifyBucket() {
        // 5 verification attempts per 15 minutes
        Refill refill = Refill.intervally(5, Duration.ofMinutes(15));
        Bandwidth limit = Bandwidth.classic(5, refill);
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createGlobalBucket() {
        // 100 requests per minute
        Refill refill = Refill.greedy(100, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(100, refill);
        return Bucket.builder().addLimit(limit).build();
    }
}
