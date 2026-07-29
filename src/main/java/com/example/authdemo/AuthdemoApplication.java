package com.example.authdemo;

import com.example.authdemo.config.SimpleSshTunnel;
import com.example.authdemo.model.User;
import com.example.authdemo.repository.UserRepository;
import com.example.authdemo.service.EmailService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication; // Předpokládám, že tohle tam máš
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.authdemo.repository.CompanyRepository;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class AuthdemoApplication {
    private EmailService emailService;
    public static void main(String[] args) {
        SimpleSshTunnel.start();
        SpringApplication.run(AuthdemoApplication.class, args);

    }


    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, CompanyRepository companyRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String superAdminEmail = "info@servisbozp.cz";
            if (userRepository.findByEmailAndDeletedAtIsNull(superAdminEmail).isEmpty()) {
                System.out.println("--- Vytvářím SUPER ADMINA ---");
                User superAdmin = new User();
                superAdmin.setFirstName("Martin");
                superAdmin.setLastName("Křesina");
                superAdmin.setEmail(superAdminEmail);
                superAdmin.setPassword(passwordEncoder.encode("servisbozp2026!"));
                superAdmin.setPhone("737683844"); // Doplň libovolné číslo, musí být unikátní

                // Nastavíme mu nějaký defaultní klíč, třeba "SUPER_ADMIN_LOBBY"
                // Tento klíč nebude mít žádná firma, takže zpočátku neuvidí žádná auta,
                // dokud si nevybere firmu v dashboardu.
                superAdmin.setKey("SUPER_ADMIN_LOBBY");

                superAdmin.setRole("SUPER_ADMIN"); // Nová role
                superAdmin.setDeletedAt(null);

                superAdmin.setGdprAccepted(true);
                superAdmin.setGdprAcceptedAt(LocalDateTime.now());
                superAdmin.setTermsAccepted(true);
                superAdmin.setTermsAcceptedAt(LocalDateTime.now());

                superAdmin.setVerificated(true);
                superAdmin.setVerificationKey("super-admin-verification");

                userRepository.save(superAdmin);
                System.out.println("--- SUPER ADMIN VYTVOŘEN ---");
            }
        };
    }
}
