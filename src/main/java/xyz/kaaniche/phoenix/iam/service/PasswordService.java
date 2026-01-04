package xyz.kaaniche.phoenix.iam.service;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordService {
    private final Argon2 argon2 = Argon2Factory.create();

    public String hashPassword(String password) {
        return argon2.hash(10, 65536, 1, password.toCharArray());
    }

    public boolean verifyPassword(String hash, String password) {
        return argon2.verify(hash, password.toCharArray());
    }
}
