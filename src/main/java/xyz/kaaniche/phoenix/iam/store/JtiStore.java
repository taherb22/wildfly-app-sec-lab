package xyz.kaaniche.phoenix.iam.store;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JTI (JWT ID) Store for replay attack prevention.
 * Stores JWT IDs with their expiration times to detect and prevent token replay.
 */
@ApplicationScoped
public class JtiStore {
    private static final String REDIS_KEY_PREFIX = "jti:";
    
    @Inject
    private RedisClient redisClient;
    
    private final Map<String, Long> inMemory = new ConcurrentHashMap<>();
    private boolean useRedis;
    
    @PostConstruct
    public void init() {
        Config config = ConfigProvider.getConfig();
        String store = config.getOptionalValue("jti.store", String.class).orElse("redis");
        useRedis = "redis".equalsIgnoreCase(store) && redisClient.isEnabled();
    }
    
    /**
     * Check if a JTI already exists (replay attack detection)
     */
    public boolean exists(String jti) {
        if (useRedis) {
            return redisClient.execute(jedis -> {
                String key = redisClient.prefix(REDIS_KEY_PREFIX + jti);
                return jedis.exists(key);
            });
        }
        
        // Clean expired entries
        long now = Instant.now().getEpochSecond();
        inMemory.entrySet().removeIf(entry -> entry.getValue() < now);
        
        return inMemory.containsKey(jti);
    }
    
    /**
     * Store a JTI with its expiration time
     */
    public void store(String jti, long expiresAtEpoch) {
        long now = Instant.now().getEpochSecond();
        long ttl = expiresAtEpoch - now;
        
        if (ttl <= 0) {
            return; // Already expired
        }
        
        if (useRedis) {
            redisClient.execute(jedis -> {
                String key = redisClient.prefix(REDIS_KEY_PREFIX + jti);
                jedis.setex(key, (int) ttl, "1");
                return null;
            });
        } else {
            inMemory.put(jti, expiresAtEpoch);
        }
    }
    
    /**
     * Remove a JTI (for testing or explicit revocation)
     */
    public void remove(String jti) {
        if (useRedis) {
            redisClient.execute(jedis -> {
                String key = redisClient.prefix(REDIS_KEY_PREFIX + jti);
                jedis.del(key);
                return null;
            });
        } else {
            inMemory.remove(jti);
        }
    }
}
