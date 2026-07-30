package com.example.authdemo.controller;

import com.example.authdemo.model.User;
import com.example.authdemo.service.EmailService;
import com.example.authdemo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChangePasswordController {
    @Autowired
    UserService userService;

    @Autowired
    EmailService emailService;

    @GetMapping("/changePassword")
    public String loginForm(Model model) {
        model.addAttribute("pageTitle", "Reset hesla");
        return "changePassword";
    }

    @PostMapping("/auth/send-verification")
    public String changePasswordRequest(@RequestParam String email, Model model, HttpSession session) {
        Optional<User> user = userService.findByEmail(email);
        if (user.isPresent()) {
            emailService.sendVerificationEmailViaEmail(email);
        }

        session.setAttribute("verificationType", "PASSWORD_RESET");
        session.setAttribute("email", email);
        session.setAttribute("verificationAttempts", 0);
        session.setAttribute("verificationIssuedAt", System.currentTimeMillis());
        session.setMaxInactiveInterval(15 * 60);
        return "redirect:/verification";
    }

    @GetMapping("/newPassword")
    public String newPassword(Model model, HttpSession session) {
        Boolean allowed = (Boolean) session.getAttribute("pw_reset_allowed");
        if (allowed == null || !allowed) {
            return "redirect:/login";
        }
        model.addAttribute("pageTitle", "Změna hesla");
        return "newPassword";
    }

    @PostMapping("/auth/new-password")
    public String changePasswordRequest(
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {

        Boolean allowed = (Boolean) session.getAttribute("pw_reset_allowed");
        String email = (String) session.getAttribute("pw_reset_email");
        if (allowed == null || !allowed || email == null) {
            redirectAttributes.addFlashAttribute("error", "Neplatná session. Zopakuj reset.");
            return "redirect:/login";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Hesla se neshodují.");
            return "newPassword";
        }
        if (!userService.isPasswordAcceptable(newPassword)) {
            model.addAttribute("error", "Heslo musí mít alespoň 12 znaků.");
            return "newPassword";
        }

        userService.changePassword(email, newPassword);
        session.invalidate();
        request.getSession(true);
        redirectAttributes.addFlashAttribute("success", "Heslo bylo úspěšně změněno!");
        return "redirect:/login";
    }
}
