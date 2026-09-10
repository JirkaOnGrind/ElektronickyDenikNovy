package com.example.authdemo.controller;

import com.example.authdemo.repository.UserRepository;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {
    private final UserRepository userRepository;

    public GlobalModelAttributes(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("loggedInUserName")
    public String loggedInUserName(Principal principal) {
        if (principal == null) return null;
        return userRepository.findByEmailAndDeletedAtIsNull(principal.getName())
                .map(user -> user.getFirstName() + " " + user.getLastName())
                .orElse(principal.getName());
    }
}
