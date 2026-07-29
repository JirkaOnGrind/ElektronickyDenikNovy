package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserCleanupService {
    private final UserRepository userRepository;
    private final UserService userService;

    public UserCleanupService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Scheduled(fixedDelay = 900000)
    public void deleteUnverifiedUsers() {
        System.out.println("SPUSTENO----------------------------------------");
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        List<User> expiredUsers =
                userRepository.findByVerificatedFalseAndDeletedAtIsNullAndTermsAcceptedAtBefore(threshold);

        for (User user : expiredUsers) {
            try {
                userService.softDelete(user.getId());
            } catch (Exception ex) {
                System.err.println("Chyba pri cleanup mazani usera " + user.getId() + ": " + ex.getMessage());
            }
        }
    }
}
