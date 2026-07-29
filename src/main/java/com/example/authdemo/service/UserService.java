package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.model.Vehicle;
import com.example.authdemo.repository.CompanyRepository;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {
    @Autowired
    CompanyService companyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("Spring Security hleda uzivatele: " + username);

        Optional<User> dbUser = userRepository.findByEmailAndDeletedAtIsNull(username);
        if (dbUser.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        User user = dbUser.get();

        if (!user.isVerificated()) {
            System.out.println("Pokus o prihlaseni neovereneho uzivatele: " + username);
            throw new DisabledException("User is not verified: " + username);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }

    public String registerUser(User user) {
        System.out.println("Register pro uzivatele: " + user.getEmail());

        user.setEmail(normalizeRequiredText(user.getEmail()));
        user.setFirstName(normalizeRequiredText(user.getFirstName()));
        user.setLastName(normalizeRequiredText(user.getLastName()));
        user.setKey(normalizeRequiredText(user.getKey()));
        user.setPhone(normalizeOptionalText(user.getPhone()));

        Optional<User> existingUserByEmail = userRepository.findByEmailAndDeletedAtIsNull(user.getEmail());
        if (existingUserByEmail.isPresent()) {
            return "email_exists";
        }

        if (user.getPhone() != null) {
            Optional<User> existingUserByPhone = userRepository.findByPhoneAndDeletedAtIsNull(user.getPhone());
            if (existingUserByPhone.isPresent()) {
                return "phone_exists";
            }
        }

        if (!"OWNER".equals(user.getRole()) && !companyRepository.existsByKey(user.getKey())) {
            return "invalid_key";
        }

        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        userRepository.save(user);
        return "success";
    }

    private String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email);
    }

    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhoneAndDeletedAtIsNull(phone);
    }

    @Transactional
    public void deleteUserAndRelatedData(Long userId) {
        try {
            companyService.deleteCompanyByUserId(userId);
            softDelete(userId);
            System.out.println("User and related data smazany pro userId: " + userId);
        } catch (Exception e) {
            System.err.println("Chyba pri mazani usera a related data: " + e.getMessage());
        }
    }

    public Optional<User> changePassword(String email, String newPassword) {
        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public void softDelete(Long id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        LocalDateTime deletedAt = LocalDateTime.now();

        for (Vehicle vehicle : vehicleRepository.findByCompanyKeyAndDeletedAtIsNull(user.getKey())) {
            vehicle.removeUserAccess(user);
            vehicle.removeVehicleAdmin(user);
            vehicleRepository.save(vehicle);
        }

        user.setDeletedAt(deletedAt);
        user.setVerificated(false);
        user.setEmail(buildArchivedValue(user.getEmail(), user.getId(), deletedAt, "deleted-user-email"));
        if (user.getPhone() != null) {
            user.setPhone(buildArchivedValue(user.getPhone(), user.getId(), deletedAt, "deleted-user-phone"));
        }
        userRepository.save(user);
    }

    public void changeRole(Long id, String role) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        user.setRole(role);
        userRepository.save(user);
    }

    public String updateUserBySuperAdmin(Long id,
                                         String firstName,
                                         String lastName,
                                         String email,
                                         String phone,
                                         String newPassword) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        String normalizedFirstName = normalizeRequiredText(firstName);
        String normalizedLastName = normalizeRequiredText(lastName);
        String normalizedEmail = normalizeRequiredText(email);
        String normalizedPhone = normalizeOptionalText(phone);
        String normalizedPassword = normalizeOptionalText(newPassword);

        if (normalizedFirstName == null || normalizedFirstName.isBlank()
                || normalizedLastName == null || normalizedLastName.isBlank()
                || normalizedEmail == null || normalizedEmail.isBlank()) {
            return "missing_required_fields";
        }

        Optional<User> userWithEmail = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail);
        if (userWithEmail.isPresent() && !userWithEmail.get().getId().equals(user.getId())) {
            return "email_exists";
        }

        if (normalizedPhone != null) {
            Optional<User> userWithPhone = userRepository.findByPhoneAndDeletedAtIsNull(normalizedPhone);
            if (userWithPhone.isPresent() && !userWithPhone.get().getId().equals(user.getId())) {
                return "phone_exists";
            }
        }

        user.setFirstName(normalizedFirstName);
        user.setLastName(normalizedLastName);
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);

        if (normalizedPassword != null) {
            user.setPassword(passwordEncoder.encode(normalizedPassword));
        }

        userRepository.save(user);
        return "success";
    }

    private String buildArchivedValue(String originalValue, Long id, LocalDateTime deletedAt, String prefix) {
        String safeOriginalValue = originalValue == null ? "empty" : originalValue.trim().replace(" ", "_");
        String timestamp = deletedAt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + "-" + id + "-" + timestamp + "-" + safeOriginalValue;
    }
}
