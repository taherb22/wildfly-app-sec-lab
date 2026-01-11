package xyz.kaaniche.phoenix.iam.abac;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * ABAC Policy Store - stores and manages policies
 * In production, this would integrate with a database or policy server
 */
@ApplicationScoped
public class PolicyStore {
    private static final Logger LOGGER = Logger.getLogger(PolicyStore.class.getName());
    private final Map<String, AbacPolicy> policies = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // Load default policies
        loadDefaultPolicies();
    }
    
    private void loadDefaultPolicies() {
        // Example policies
        
        // Policy 1: Admin users can access everything
        AbacPolicy adminPolicy = new AbacPolicy(
                "policy-admin-full-access",
                "Admin Full Access",
                "PERMIT",
                List.of(new AbacPolicy.Condition("user.role", "equals", "ADMIN"))
        );
        addPolicy(adminPolicy);
        
        // Policy 2: Users can access their own resources
        AbacPolicy userSelfAccessPolicy = new AbacPolicy(
                "policy-user-self-access",
                "User Self Access",
                "PERMIT",
                List.of(
                        new AbacPolicy.Condition("user.id", "equals", "${resource.ownerId}"),
                        new AbacPolicy.Condition("user.role", "in", List.of("USER", "ADMIN"))
                )
        );
        addPolicy(userSelfAccessPolicy);
        
        // Policy 3: Deny access outside business hours (example)
        AbacPolicy businessHoursPolicy = new AbacPolicy(
                "policy-business-hours",
                "Business Hours Only",
                "DENY",
                List.of(
                        new AbacPolicy.Condition("environment.time.hour", "lessThan", 8),
                        new AbacPolicy.Condition("resource.type", "equals", "sensitive-data")
                )
        );
        // Don't add this one by default - it's just an example
        
        LOGGER.info("Loaded " + policies.size() + " default ABAC policies");
    }
    
    public void addPolicy(AbacPolicy policy) {
        policies.put(policy.getId(), policy);
        LOGGER.info("Added policy: " + policy.getName());
    }
    
    public void removePolicy(String policyId) {
        policies.remove(policyId);
        LOGGER.info("Removed policy: " + policyId);
    }
    
    public AbacPolicy getPolicy(String policyId) {
        return policies.get(policyId);
    }
    
    public List<AbacPolicy> getAllPolicies() {
        return new ArrayList<>(policies.values());
    }
    
    public void clear() {
        policies.clear();
    }
}
