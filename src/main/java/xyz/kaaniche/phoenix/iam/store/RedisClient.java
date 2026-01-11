package xyz.kaaniche.phoenix.iam.store;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Optional;
import java.util.function.Function;

@ApplicationScoped
public class RedisClient {
    private JedisPool pool;
    private boolean enabled;
    private String keyPrefix;
    private int database;

    @PostConstruct
    public void init() {
        Config config = ConfigProvider.getConfig();
        enabled = config.getOptionalValue("redis.enabled", Boolean.class).orElse(false);
        keyPrefix = config.getOptionalValue("redis.key.prefix", String.class).orElse("phoenix:iam:");
        database = config.getOptionalValue("redis.database", Integer.class).orElse(0);
        if (!enabled) {
            return;
        }
        String host = config.getOptionalValue("redis.host", String.class).orElse("localhost");
        int port = config.getOptionalValue("redis.port", Integer.class).orElse(6379);
        int timeoutMs = config.getOptionalValue("redis.timeout.ms", Integer.class).orElse(2000);
        boolean ssl = config.getOptionalValue("redis.ssl", Boolean.class).orElse(false);
        Optional<String> username = config.getOptionalValue("redis.username", String.class);
        Optional<String> password = config.getOptionalValue("redis.password", String.class);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getOptionalValue("redis.pool.maxTotal", Integer.class).orElse(16));
        poolConfig.setMaxIdle(config.getOptionalValue("redis.pool.maxIdle", Integer.class).orElse(8));
        poolConfig.setMinIdle(config.getOptionalValue("redis.pool.minIdle", Integer.class).orElse(0));

        if (username.isPresent()) {
            pool = new JedisPool(poolConfig, host, port, timeoutMs, username.get(), password.orElse(null), database, null, ssl);
        } else {
            pool = new JedisPool(poolConfig, host, port, timeoutMs, password.orElse(null), database, ssl);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String prefix(String key) {
        return keyPrefix + key;
    }

    public <T> T execute(Function<Jedis, T> fn) {
        if (!enabled) {
            throw new IllegalStateException("Redis is disabled");
        }
        try (Jedis jedis = pool.getResource()) {
            if (database != 0) {
                jedis.select(database);
            }
            return fn.apply(jedis);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (pool != null) {
            pool.close();
        }
    }
}
