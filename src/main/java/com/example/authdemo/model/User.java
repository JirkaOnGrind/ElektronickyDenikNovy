package com.example.authdemo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public enum Position {
        USER("Uživatel"),
        MAINTENANCE("Údržba");

        private final String displayName;

        Position(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final String ROLE_MAINTENANCE = Position.MAINTENANCE.name();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number", unique = true)
    private String phone;

    @Column(name = "workplace")
    private String workplace;

    @Column(name = "user_key", nullable=false)
    private String key;

    @Column(nullable = false)
    @ToString.Exclude
    private String password;

    @Column
    private String role;

    @Column(nullable = false)
    @ToString.Exclude
    private String verificationKey;

    @Column(nullable = false)
    private boolean verificated;

    @Column(name = "gdpr_accepted", nullable = false)
    private boolean gdprAccepted;

    @Column(name = "gdpr_accepted_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime gdprAcceptedAt;

    @Column(name = "terms_accepted", nullable = false)
    private boolean termsAccepted;

    @Column(name = "terms_accepted_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "deleted_at", columnDefinition = "DATETIME")
    private LocalDateTime deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_dismissed_daily_checks",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "daily_check_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<DailyCheck> dismissedDefects = new HashSet<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public boolean isVerificated() {
        return verificated;
    }

    public void setVerificated(boolean verificated) {
        this.verificated = verificated;
    }

    public String getVerificationKey() {
        return verificationKey;
    }

    public void setVerificationKey(String verificationKey) {
        this.verificationKey = verificationKey;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isGdprAccepted() {
        return gdprAccepted;
    }

    public void setGdprAccepted(boolean gdprAccepted) {
        this.gdprAccepted = gdprAccepted;
    }

    public LocalDateTime getGdprAcceptedAt() {
        return gdprAcceptedAt;
    }

    public void setGdprAcceptedAt(LocalDateTime gdprAcceptedAt) {
        this.gdprAcceptedAt = gdprAcceptedAt;
    }

    public boolean isTermsAccepted() {
        return termsAccepted;
    }

    public void setTermsAccepted(boolean termsAccepted) {
        this.termsAccepted = termsAccepted;
    }

    public LocalDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(LocalDateTime termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    // Aktualizované konstruktory
    public User(String firstName, String lastName, String email, String phone, String password, String key) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.role = ROLE_USER;
        this.gdprAccepted = true;
        this.termsAccepted = true;
        this.gdprAcceptedAt = LocalDateTime.now();
        this.termsAcceptedAt = LocalDateTime.now();
        this.key = key;
        this.verificationKey = generateVerificationCode();
        this.verificated = false;
        this.deletedAt = null;
    }



    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    // GENERACE VERIFIKACNIHO KODU
    public static String generateVerificationCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000; // 100000–999999
        return String.valueOf(code);
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(this.role);
    }

    public boolean isMaintenance() {
        return ROLE_MAINTENANCE.equalsIgnoreCase(this.role);
    }

    public void dismissDefect(DailyCheck dailyCheck) {
        dismissedDefects.add(dailyCheck);
    }
}
