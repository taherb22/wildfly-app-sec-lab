package xyz.kaaniche.phoenix.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

public class CspNonceFilter implements Filter {

    private static final SecureRandom RANDOM = new SecureRandom();

    private String generateNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String nonce = generateNonce();
        request.setAttribute("cspNonce", nonce);

        response.setHeader("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' https://unpkg.com 'nonce-" + nonce + "'; " +
            "style-src 'self'; " +
            "connect-src 'self'; " +
            "img-src 'self' data:; " +
            "object-src 'none'; " +
            "base-uri 'self'; " +
            "frame-ancestors 'none'; " +
            "report-uri /phoenix-iam/csp-report;"
        );

        chain.doFilter(request, response);
    }
}
