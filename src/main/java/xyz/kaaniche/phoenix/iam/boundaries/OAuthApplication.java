package xyz.kaaniche.phoenix.iam.boundaries;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS Application for OAuth 2.1 endpoints at root path.
 * Endpoints: /authorize, /login/authorization, /oauth/token, /mfa/*
 */
@ApplicationPath("/")
public class OAuthApplication extends Application {
    // Empty - uses default JAX-RS resource discovery
}
