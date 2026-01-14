package xyz.kaaniche.phoenix.iam.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import xyz.kaaniche.phoenix.iam.store.RedisClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RateLimiter {
    private static final String REDIS_KEY_PREFIX = "ratelimit:";

    @Inject
    private RedisClient redisClient;

    private int maxAttempts;
    private int windowSeconds;
    private boolean useRedis;
    private final Map<String, Attempt> inMemory = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Config config = ConfigProvider.getConfig();
        maxAttempts = config.getOptionalValue("rate.limit.maxAttempts", Integer.class).orElse(5);
        windowSeconds = config.getOptionalValue("rate.limit.windowSeconds", Integer.class).orElse(900);
        String store = config.getOptionalValue("rate.limit.store", String.class).orElse("redis");
        useRedis = "redis".equalsIgnoreCase(store) && redisClient.isEnabled();
    }

    public RateLimitResult check(String key) {
        if (useRedis) {
            return redisClient.execute(jedis -> {
                String redisKey = redisClient.prefix(REDIS_KEY_PREFIX + key);
                long count = jedis.incr(redisKey);
                if (count == 1L) {
                    jedis.expire(redisKey, windowSeconds);
                }
                long ttl = jedis.ttl(redisKey);
                boolean allowed = count <= maxAttempts;
                long remaining = Math.max(0, maxAttempts - count);
                long retryAfter = ttl > 0 ? ttl : windowSeconds;
                return new RateLimitResult(allowed, remaining, retryAfter);
            });
        }
        return checkInMemory(key);
    }

    private RateLimitResult checkInMemory(String key) {
        long now = Instant.now().getEpochSecond();
        Attempt attempt = inMemory.computeIfAbsent(key, k -> new Attempt(now, 0));
        synchronized (attempt) {
            if (now - attempt.windowStartEpoch >= windowSeconds) {
                attempt.windowStartEpoch = now;
                attempt.count = 0;
            }
            attempt.count++;
            boolean allowed = attempt.count <= maxAttempts;
            long remaining = Math.max(0, maxAttempts - attempt.count);
            long retryAfter = Math.max(1, windowSeconds - (now - attempt.windowStartEpoch));
            return new RateLimitResult(allowed, remaining, retryAfter);
        }
    }

    private static class Attempt {
        private long windowStartEpoch;
        private int count;

        private Attempt(long windowStartEpoch, int count) {
            this.windowStartEpoch = windowStartEpoch;
            this.count = count;
        }
    }

    public record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {
    }
}
