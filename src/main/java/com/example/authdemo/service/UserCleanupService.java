package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserCleanupService {
    private static final Logger log = LoggerFactory.getLogger(UserCleanupService.class);

    private final UserRepository userRepository;
    private final UserService userService;

    public UserCleanupService(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Scheduled(fixedDelay = 900000)
    public void deleteUnverifiedUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        List<User> expiredUsers =
                userRepository.findByVerificatedFalseAndDeletedAtIsNullAndTermsAcceptedAtBefore(threshold);

        for (User user : expiredUsers) {
            try {
                userService.softDelete(user.getId());
            } catch (Exception ex) {
                log.error("Scheduled unverified-user cleanup failed: {}",
                        ex.getClass().getSimpleName());
            }
        }
    }
}
