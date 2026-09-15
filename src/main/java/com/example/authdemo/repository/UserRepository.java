package com.example.authdemo.repository;

import com.example.authdemo.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    @EntityGraph(attributePaths = "dismissedDefects")
    @Query("select u from User u where u.email = :email and u.deletedAt is null")
    Optional<User> findByEmailWithDismissedDefects(@Param("email") String email);
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    Optional<User> findById(Long id);
    Optional<User> findByIdAndDeletedAtIsNull(Long id);
    Optional<User> findByPhone(String phone);
    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);
    List<User> findByRoleAndKeyAndDeletedAtIsNull(String role, String key);
    boolean existsByEmail(String email);
    boolean existsByEmailAndDeletedAtIsNull(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndDeletedAtIsNull(String phone);
    List<User> findByRoleNot(String role);
    List<User> findByKeyAndDeletedAtIsNull(String key);
    List<User> findByVerificatedFalseAndDeletedAtIsNullAndTermsAcceptedAtBefore(@Param("threshold") LocalDateTime threshold);
}
