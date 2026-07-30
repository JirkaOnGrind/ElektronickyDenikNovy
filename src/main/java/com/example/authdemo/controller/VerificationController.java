package com.example.authdemo.controller;

import com.example.authdemo.service.EmailService;
import com.example.authdemo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class VerificationController {
    private static final long VERIFICATION_VALIDITY_MILLIS = 15 * 60 * 1000L;
    private static final long RESEND_COOLDOWN_MILLIS = 60 * 1000L;
    private static final int MAX_RESENDS_PER_SESSION = 5;

    @Autowired
    UserService userService;
    private final EmailService emailService;
    public VerificationController(EmailService emailService) {
        this.emailService = emailService;
    }

    // REGISTER USER ----------------------------------------------------------
    @GetMapping("/verification")
    public String verificationForm(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("pendingVerificationUserId");
        String verificationType = (String) session.getAttribute("verificationType");
        if (userId == null && !"PASSWORD_RESET".equals(verificationType)) {
            return "redirect:/register";
        }
        model.addAttribute("pageTitle", "Verifikace");
        return "verificationEmail";
    }
    @PostMapping("/auth/verification")
    public String verifyUser(@RequestParam String code, HttpSession session, Model model, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        String verificationType = (String) session.getAttribute("verificationType");
        String email = (String) session.getAttribute("email");
        Long userId = (Long) session.getAttribute("pendingVerificationUserId");

        if (userId == null && !"PASSWORD_RESET".equals(verificationType)) {
            return "redirect:/register";
        }

        Long issuedAt = (Long) session.getAttribute("verificationIssuedAt");
        if (issuedAt == null || System.currentTimeMillis() - issuedAt > VERIFICATION_VALIDITY_MILLIS) {
            session.removeAttribute("verificationType");
            session.removeAttribute("email");
            session.removeAttribute("pendingVerificationUserId");
            session.removeAttribute("verificationAttempts");
            model.addAttribute("pageTitle", "Verifikace");
            model.addAttribute("error", "Ověřovací kód vypršel. Vyžádejte si nový kód.");
            return "verificationEmail";
        }

        // Načtení počtu pokusů
        Integer attempts = (Integer) session.getAttribute("verificationAttempts");
        attempts = (attempts == null) ? 1 : attempts + 1;

        // Limit pokusů
        if (attempts > 3) {
            session.removeAttribute("verificationAttempts");

            if ("PASSWORD_RESET".equals(verificationType)) {
                // Reset hesla → zablokovat reset
                session.removeAttribute("verificationType");
                session.removeAttribute("email");
                model.addAttribute("error", "Překročen maximální počet pokusů pro reset hesla. Zkuste to později.");
                return "verificationEmail";
            } else {
                // Registrace → smazat uživatele
                if (userId != null) {
                    userService.deleteUserAndRelatedData(userId);
                }
                session.removeAttribute("verificationType");
                session.removeAttribute("email");
                session.removeAttribute("pendingVerificationUserId");
                model.addAttribute("error", "Překročen limit pokusů. Registrujte se znovu.");
                return "verificationEmail";
            }
        }

        // Uložení nového počtu pokusů
        session.setAttribute("verificationAttempts", attempts);

        // Ověření kódu
        boolean verified = false;
        if (code != null && code.matches("\\d{6}") && "PASSWORD_RESET".equals(verificationType)) {
            verified = emailService.checkVerificationCodeViaEmail(email, code);
        } else if (code != null && code.matches("\\d{6}") && userId != null) {
            verified = emailService.checkVerificationCode(userId, code);
        }

        if (verified) {
            // Úspěch → vyčištění session
            session.removeAttribute("verificationAttempts");
            if ("PASSWORD_RESET".equals(verificationType)) {
                session.removeAttribute("verificationType");
                session.removeAttribute("email");

                // invalidate old session (prevence session fixation)
                session.invalidate();

                // create new session and set allowed flag
                HttpSession newSession = request.getSession(true);
                newSession.setAttribute("pw_reset_allowed", Boolean.TRUE);
                newSession.setAttribute("pw_reset_email", email); // optional: safe because code was verified
                newSession.setMaxInactiveInterval(10 * 60); // 10 minutes

                return "redirect:/newPassword";
            } else {
                session.removeAttribute("verificationType");
                session.removeAttribute("email");
                session.removeAttribute("pendingVerificationUserId");
                redirectAttributes.addFlashAttribute("success", "Účet byl úspěšně ověřen!");
                return "redirect:/login";
            }
        } else {
            // Neúspěch → zobrazit chybu s počtem zbývajících pokusů
            model.addAttribute("pageTitle", "Verifikace");
            model.addAttribute("error", "Zadaný kód není správný. Zbývající pokusy: " + (3 - attempts));
            return "verificationEmail";
        }
    }

    @PostMapping("/auth/verification/sendAgain")
    public String sendVerifyCodeAgain(HttpSession session, Model model) {
        Long userId = (Long) session.getAttribute("pendingVerificationUserId");
        String verificationType = (String) session.getAttribute("verificationType");
        String email = (String) session.getAttribute("email");

        long now = System.currentTimeMillis();
        Long lastResendAt = (Long) session.getAttribute("verificationResendAt");
        Integer resendCount = (Integer) session.getAttribute("verificationResendCount");
        resendCount = resendCount == null ? 0 : resendCount;

        if (lastResendAt != null && now - lastResendAt < RESEND_COOLDOWN_MILLIS) {
            model.addAttribute("pageTitle", "Verifikace");
            model.addAttribute("error", "Další kód lze odeslat nejdříve za jednu minutu.");
            return "verificationEmail";
        }
        if (resendCount >= MAX_RESENDS_PER_SESSION) {
            model.addAttribute("pageTitle", "Verifikace");
            model.addAttribute("error", "Byl dosažen limit opakovaného odeslání kódu.");
            return "verificationEmail";
        }

        if ("PASSWORD_RESET".equals(verificationType)) {
            if (email == null) {
                return "redirect:/login";
            }
            emailService.sendVerificationEmailViaEmail(email);
        } else if (userId != null) {
            emailService.sendVerificationEmailViaId(userId);
        }
        else
        {
            return "redirect:/login";
        }
        session.setAttribute("verificationIssuedAt", now);
        session.setAttribute("verificationResendAt", now);
        session.setAttribute("verificationResendCount", resendCount + 1);
        session.setAttribute("verificationAttempts", 0);
        model.addAttribute("sendAgain", "Kód byl odeslán znovu na váš email.");
        return "verificationEmail";
    }
}
