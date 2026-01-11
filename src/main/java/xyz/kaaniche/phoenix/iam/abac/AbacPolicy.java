package xyz.kaaniche.phoenix.iam.abac;

import java.util.List;
import java.util.Map;

/**
 * ABAC Policy model - represents an access control policy
 */
public class AbacPolicy {
    private String id;
    private String name;
    private String description;
    private String effect; // PERMIT or DENY
    private List<Condition> conditions;
    
    public static class Condition {
        private String attribute; // e.g., "user.role", "resource.type", "environment.time"
        private String operator;  // e.g., "equals", "contains", "in", "matches"
        private Object value;
        
        public Condition() {}
        
        public Condition(String attribute, String operator, Object value) {
            this.attribute = attribute;
            this.operator = operator;
            this.value = value;
        }
        
        public String getAttribute() {
            return attribute;
        }
        
        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }
        
        public String getOperator() {
            return operator;
        }
        
        public void setOperator(String operator) {
            this.operator = operator;
        }
        
        public Object getValue() {
            return value;
        }
        
        public void setValue(Object value) {
            this.value = value;
        }
    }
    
    public AbacPolicy() {}
    
    public AbacPolicy(String id, String name, String effect, List<Condition> conditions) {
        this.id = id;
        this.name = name;
        this.effect = effect;
        this.conditions = conditions;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getEffect() {
        return effect;
    }
    
    public void setEffect(String effect) {
        this.effect = effect;
    }
    
    public List<Condition> getConditions() {
        return conditions;
    }
    
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }
}
