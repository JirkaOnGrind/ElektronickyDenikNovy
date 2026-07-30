package com.example.authdemo;

import com.example.authdemo.config.SimpleSshTunnel;
import com.example.authdemo.model.User;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;

@SpringBootApplication
@EnableScheduling
public class AuthdemoApplication {
    private static final Logger log = LoggerFactory.getLogger(AuthdemoApplication.class);

    public static void main(String[] args) {
        SimpleSshTunnel.start();
        SpringApplication.run(AuthdemoApplication.class, args);
    }

    @Bean
    CommandLineRunner initDatabase(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserService userService) {
        return args -> {
            String superAdminEmail = getEnvironmentValue("SUPER_ADMIN_EMAIL");
            String initialPassword = getEnvironmentValue("SUPER_ADMIN_INITIAL_PASSWORD");

            if (superAdminEmail == null || initialPassword == null) {
                log.info("Inicializace superadmina přeskočena; účet se vytvoří pouze při nastavení SUPER_ADMIN_EMAIL a SUPER_ADMIN_INITIAL_PASSWORD.");
                return;
            }
            if (!userService.isPasswordAcceptable(initialPassword)) {
                log.error("SUPER_ADMIN_INITIAL_PASSWORD musí mít alespoň 12 znaků.");
                return;
            }

            if (userRepository.findByEmailAndDeletedAtIsNull(superAdminEmail).isEmpty()) {
                log.warn("Vytvářím počáteční účet SUPER_ADMIN z proměnných prostředí.");
                User superAdmin = new User();
                superAdmin.setFirstName("Super");
                superAdmin.setLastName("Admin");
                superAdmin.setEmail(superAdminEmail);
                superAdmin.setPassword(passwordEncoder.encode(initialPassword));
                superAdmin.setPhone(null);
                superAdmin.setKey("SUPER_ADMIN_LOBBY");
                superAdmin.setRole("SUPER_ADMIN");
                superAdmin.setDeletedAt(null);
                superAdmin.setGdprAccepted(true);
                superAdmin.setGdprAcceptedAt(LocalDateTime.now());
                superAdmin.setTermsAccepted(true);
                superAdmin.setTermsAcceptedAt(LocalDateTime.now());
                superAdmin.setVerificated(true);
                superAdmin.setVerificationKey(passwordEncoder.encode(User.generateVerificationCode()));
                userRepository.save(superAdmin);
                log.warn("Počáteční účet SUPER_ADMIN byl vytvořen; po prvním přihlášení změňte jeho heslo.");
            }
        };
    }

    private String getEnvironmentValue(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
