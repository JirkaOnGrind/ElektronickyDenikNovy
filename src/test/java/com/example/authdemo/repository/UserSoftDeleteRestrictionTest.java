package com.example.authdemo.repository;

import com.example.authdemo.model.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserSoftDeleteRestrictionTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletedUserIsExcludedFromStandardJpaQueries() {
        User user = new User("Soft", "Deleted", "deleted@example.invalid", null,
                "encoded-password", "company");
        user.setVerificated(true);
        userRepository.saveAndFlush(user);

        entityManager.createNativeQuery("update users set deleted_at = :deletedAt where id = :id")
                .setParameter("deletedAt", LocalDateTime.now())
                .setParameter("id", user.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(userRepository.findAll()).noneMatch(found -> found.getId().equals(user.getId()));
    }
}
