package xyz.kaaniche.phoenix.iam.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import xyz.kaaniche.phoenix.iam.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UserService {
    
    @PersistenceContext
    private EntityManager em;

    @Inject
    private PasswordService passwordService;

    @Transactional
    public User createUser(String username, String email, String password, String... roles) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordService.hashPassword(password));
        user.getRoles().addAll(List.of(roles));
        em.persist(user);
        return user;
    }

    public Optional<User> findByUsername(String username) {
        return em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(em.find(User.class, id));
    }

    public List<User> findAll() {
        return em.createQuery("SELECT u FROM User u", User.class).getResultList();
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setLastLogin(LocalDateTime.now());
            em.merge(user);
        }
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = em.find(User.class, userId);
        if (user != null) {
            em.remove(user);
        }
    }

    public boolean authenticate(String username, String password) {
        return findByUsername(username)
                .map(user -> user.isEnabled() && passwordService.verifyPassword(user.getPasswordHash(), password))
                .orElse(false);
    }
}
