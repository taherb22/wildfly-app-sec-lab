package xyz.kaaniche.phoenix.iam.store;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.StringReader;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class LoginSessionStore {
    private static final String REDIS_KEY_PREFIX = "login:";

    @Inject
    private RedisClient redisClient;

    private final Map<String, StoredSession> inMemory = new ConcurrentHashMap<>();
    private boolean useRedis;
    private int ttlSeconds;
    private boolean sliding;

    @PostConstruct
    public void init() {
        Config config = ConfigProvider.getConfig();
        String store = config.getOptionalValue("session.store", String.class).orElse("memory");
        ttlSeconds = config.getOptionalValue("session.ttl.seconds", Integer.class).orElse(300);
        sliding = config.getOptionalValue("session.sliding", Boolean.class).orElse(true);
        useRedis = "redis".equalsIgnoreCase(store) && redisClient.isEnabled();
    }

    public String create(LoginSession session) {
        String sessionId = UUID.randomUUID().toString();
        session.setCreatedAtEpoch(Instant.now().getEpochSecond());
        String payload = serialize(session);
        if (useRedis) {
            redisClient.execute(jedis -> {
                jedis.setex(redisClient.prefix(REDIS_KEY_PREFIX + sessionId), ttlSeconds, payload);
                return null;
            });
        } else {
            inMemory.put(sessionId, new StoredSession(payload, Instant.now().plusSeconds(ttlSeconds).getEpochSecond()));
        }
        return sessionId;
    }

    public Optional<LoginSession> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        if (useRedis) {
            return redisClient.execute(jedis -> {
                String key = redisClient.prefix(REDIS_KEY_PREFIX + sessionId);
                String payload = jedis.get(key);
                if (payload == null) {
                    return Optional.empty();
                }
                if (sliding) {
                    jedis.expire(key, ttlSeconds);
                }
                return Optional.of(deserialize(payload));
            });
        }
        StoredSession stored = inMemory.get(sessionId);
        if (stored == null) {
            return Optional.empty();
        }
        if (stored.expiresAtEpoch < Instant.now().getEpochSecond()) {
            inMemory.remove(sessionId);
            return Optional.empty();
        }
        if (sliding) {
            stored.expiresAtEpoch = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        }
        return Optional.of(deserialize(stored.payload));
    }

    public void delete(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        if (useRedis) {
            redisClient.execute(jedis -> jedis.del(redisClient.prefix(REDIS_KEY_PREFIX + sessionId)));
        } else {
            inMemory.remove(sessionId);
        }
    }

    private String serialize(LoginSession session) {
        JsonObject object = Json.createObjectBuilder()
                .add("tenantName", session.getTenantName())
                .add("requestedScopes", session.getRequestedScopes())
                .add("redirectUri", session.getRedirectUri())
                .add("responseType", session.getResponseType())
                .add("codeChallenge", session.getCodeChallenge() == null ? "" : session.getCodeChallenge())
                .add("state", session.getState())
                .add("createdAtEpoch", session.getCreatedAtEpoch())
                .build();
        return object.toString();
    }

    private LoginSession deserialize(String payload) {
        JsonObject object = Json.createReader(new StringReader(payload)).readObject();
        LoginSession session = new LoginSession();
        session.setTenantName(object.getString("tenantName", null));
        session.setRequestedScopes(object.getString("requestedScopes", null));
        session.setRedirectUri(object.getString("redirectUri", null));
        session.setResponseType(object.getString("responseType", null));
        session.setCodeChallenge(object.getString("codeChallenge", null));
        session.setState(object.getString("state", null));
        session.setCreatedAtEpoch(object.getJsonNumber("createdAtEpoch").longValue());
        return session;
    }

    private static class StoredSession {
        private final String payload;
        private volatile long expiresAtEpoch;

        private StoredSession(String payload, long expiresAtEpoch) {
            this.payload = payload;
            this.expiresAtEpoch = expiresAtEpoch;
        }
    }
}
