package com.example.authdemo.service;

import com.example.authdemo.model.User;
import com.example.authdemo.repository.UserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerificationEmail(User user) {
        user.setVerificationKey(User.generateVerificationCode());
        String plainCode = user.getVerificationKey();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(senderEmail);
        message.setSubject("Ověřovací kód");
        message.setText(
                "Dobrý den, zde zasíláme kód od Elektronického Deníku: " + plainCode
                        + "\nTento kód je platný po dobu 15 minut. Po uplynutí této doby bude váš účet automaticky smazán."
        );

        String hashedVerificationKey = passwordEncoder.encode(plainCode);
        user.setVerificationKey(hashedVerificationKey);
        userRepository.save(user);

        sendMessage(message, user.getEmail(), "registration-verification");
    }

    @Async
    public void sendVerificationEmailViaId(Long userId) {
        Optional<User> dbUser = userRepository.findByIdAndDeletedAtIsNull(userId);
        if (dbUser.isEmpty()) {
            log.warn("Mail resend skipped, user not found by id={}", userId);
            return;
        }

        User user = dbUser.get();
        user.setVerificationKey(User.generateVerificationCode());
        String plainCode = user.getVerificationKey();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(senderEmail);
        message.setSubject("Ověřovací kód");
        message.setText(
                "Dobrý den, zde zasíláme kód od Elektronického Deníku: " + plainCode
                        + "\nTento kód je platný po dobu 15 minut. Po uplynutí této doby bude váš účet automaticky smazán."
        );

        String hashedVerificationKey = passwordEncoder.encode(plainCode);
        user.setVerificationKey(hashedVerificationKey);
        userRepository.save(user);

        sendMessage(message, user.getEmail(), "verification-resend-by-id");
    }

    public void sendVerificationEmailViaEmail(String email) {
        Optional<User> dbUser = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (dbUser.isEmpty()) {
            log.warn("Password reset mail skipped, user not found by email={}", email);
            return;
        }

        User user = dbUser.get();
        user.setVerificationKey(User.generateVerificationCode());
        String plainCode = user.getVerificationKey();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setFrom(senderEmail);
        message.setSubject("Ověřovací kód");
        message.setText(
                "Dobrý den, toto je váš kód od Elektronického Deníku pro reset hesla: " + plainCode
                        + "\nPokud jste o reset hesla nežádal, prosím ignorujte tento email."
        );

        String hashedVerificationKey = passwordEncoder.encode(plainCode);
        user.setVerificationKey(hashedVerificationKey);
        userRepository.save(user);

        sendMessage(message, user.getEmail(), "password-reset");
    }

    private void sendMessage(SimpleMailMessage message, String recipient, String purpose) {
        log.info("Attempting to send email purpose={} from={} to={}", purpose, senderEmail, recipient);
        try {
            mailSender.send(message);
            log.info("Email sent successfully purpose={} to={}", purpose, recipient);
        } catch (MailException ex) {
            log.error("Email sending failed purpose={} to={} message={}", purpose, recipient, ex.getMessage(), ex);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Unexpected email failure purpose={} to={} message={}", purpose, recipient, ex.getMessage(), ex);
            throw ex;
        }
    }

    public boolean checkVerificationCode(Long userId, String code) {
        log.info("Verification attempt via userId={}", userId);
        Optional<User> dbUser = userRepository.findByIdAndDeletedAtIsNull(userId);
        if (dbUser.isPresent()) {
            User user = dbUser.get();
            boolean verifyCodesMatches = passwordEncoder.matches(code, user.getVerificationKey());
            if (verifyCodesMatches) {
                log.info("Verification successful via userId={}", userId);
                user.setVerificationKey("null");
                user.setVerificated(true);
                userRepository.save(user);
            }
            return verifyCodesMatches;
        }
        log.warn("Verification failed, user not found by id={}", userId);
        return false;
    }

    public boolean checkVerificationCodeViaEmail(String email, String code) {
        log.info("Verification attempt via email={}", email);
        Optional<User> dbUser = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (dbUser.isPresent()) {
            User user = dbUser.get();
            boolean verifyCodesMatches = passwordEncoder.matches(code, user.getVerificationKey());
            if (verifyCodesMatches) {
                log.info("Verification successful via email={}", email);
                user.setVerificationKey("null");
                user.setVerificated(true);
                userRepository.save(user);
            }
            return verifyCodesMatches;
        }
        log.warn("Verification failed, user not found by email={}", email);
        return false;
    }
}
