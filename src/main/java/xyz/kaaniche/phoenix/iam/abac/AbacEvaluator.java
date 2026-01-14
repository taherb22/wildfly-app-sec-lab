package xyz.kaaniche.phoenix.iam.abac;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ABAC Policy Evaluator - evaluates policies against request context
 */
@ApplicationScoped
public class AbacEvaluator {
    private static final Logger LOGGER = Logger.getLogger(AbacEvaluator.class.getName());
    
    @Inject
    private PolicyStore policyStore;
    
    /**
     * Evaluate access decision for given context
     * Returns PERMIT if any policy permits and no policy denies
     */
    public Decision evaluate(Map<String, Object> context) {
        List<AbacPolicy> policies = policyStore.getAllPolicies();
        
        boolean hasPermit = false;
        
        for (AbacPolicy policy : policies) {
            if (matchesPolicy(policy, context)) {
                LOGGER.info("Policy matched: " + policy.getName());
                
                if ("DENY".equals(policy.getEffect())) {
                    LOGGER.warning("Access DENIED by policy: " + policy.getName());
                    return new Decision(false, "DENY", policy.getName());
                }
                
                if ("PERMIT".equals(policy.getEffect())) {
                    hasPermit = true;
                }
            }
        }
        
        if (hasPermit) {
            LOGGER.info("Access PERMITTED");
            return new Decision(true, "PERMIT", null);
        }
        
        LOGGER.info("Access DENIED - no matching PERMIT policy");
        return new Decision(false, "DENY", "No matching policy");
    }
    
    private boolean matchesPolicy(AbacPolicy policy, Map<String, Object> context) {
        if (policy.getConditions() == null || policy.getConditions().isEmpty()) {
            return true;
        }
        
        for (AbacPolicy.Condition condition : policy.getConditions()) {
            if (!evaluateCondition(condition, context)) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean evaluateCondition(AbacPolicy.Condition condition, Map<String, Object> context) {
        Object contextValue = getNestedValue(context, condition.getAttribute());
        Object expectedValue = condition.getValue();
        String operator = condition.getOperator();
        
        return switch (operator) {
            case "equals" -> contextValue != null && contextValue.equals(expectedValue);
            case "contains" -> contextValue instanceof String str && 
                    expectedValue instanceof String exp && str.contains(exp);
            case "in" -> expectedValue instanceof List list && list.contains(contextValue);
            case "matches" -> contextValue instanceof String str && 
                    expectedValue instanceof String regex && str.matches(regex);
            case "greaterThan" -> contextValue instanceof Number num1 && 
                    expectedValue instanceof Number num2 && 
                    num1.doubleValue() > num2.doubleValue();
            case "lessThan" -> contextValue instanceof Number num1 && 
                    expectedValue instanceof Number num2 && 
                    num1.doubleValue() < num2.doubleValue();
            default -> {
                LOGGER.warning("Unknown operator: " + operator);
                yield false;
            }
        };
    }
    
    private Object getNestedValue(Map<String, Object> context, String path) {
        String[] parts = path.split("\\.");
        Object current = context;
        
        for (String part : parts) {
            if (current instanceof Map map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    public static class Decision {
        private final boolean permitted;
        private final String effect;
        private final String reason;
        
        public Decision(boolean permitted, String effect, String reason) {
            this.permitted = permitted;
            this.effect = effect;
            this.reason = reason;
        }
        
        public boolean isPermitted() {
            return permitted;
        }
        
        public String getEffect() {
            return effect;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
