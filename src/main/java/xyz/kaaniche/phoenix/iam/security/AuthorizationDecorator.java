package xyz.kaaniche.phoenix.iam.security;

import java.security.Permission;

public class AuthorizationDecorator extends Permission {
    private final String resource;
    private final String action;

    public AuthorizationDecorator(String resource, String action) {
        super(resource + ":" + action);
        this.resource = resource;
        this.action = action;
    }

    @Override
    public boolean implies(java.security.Permission permission) {
        if (permission.getClass() == Permission.class) {
            Permission p = (Permission) permission;
            return this.resource.equals(p.getName()) && this.action.equals(p.getActions());
        }
        return false;
    }

    @Override
    public String getActions() {
        return action;
    }

    // hashCode and equals methods should be overridden to ensure proper comparison
    @Override
    public int hashCode() {
        return resource.hashCode() + action.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AuthorizationDecorator) {
            AuthorizationDecorator other = (AuthorizationDecorator) obj;
            return this.resource.equals(other.resource) && this.action.equals(other.action);
        }
        return false;
    }
}