package com.example.authdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // ... (veřejné endpointy zůstávají) ...
                        .requestMatchers("/login", "/auth/**", "/register", "/css/**", "/js/**","/register/company","/api/**","/dev/**","/verification","/changePassword", "/auth/verification/**","/newPassword","/privacy").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        // Povolit SUPER_ADMIN přístup do jeho sekce
                        .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")

                        // Povolit SUPER_ADMIN i do běžné administrace (aby mohl spravovat firmu)
                        .requestMatchers("/admin/vehicles/**").authenticated()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OWNER", "SUPER_ADMIN")

                        // Povolit přístup k API vozidel i pro SUPER_ADMIN
                        .requestMatchers("/vehicles/**").hasAnyRole("ADMIN", "OWNER", "USER", "SUPER_ADMIN")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            var authorities = authentication.getAuthorities();

                            // Logika přesměrování
                            boolean isSuperAdmin = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
                            boolean isAdminOrOwner = authorities.stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN") || role.getAuthority().equals("ROLE_OWNER"));

                            String targetUrl;
                            if (isSuperAdmin) {
                                targetUrl = "/super-admin/dashboard";
                            } else if (isAdminOrOwner) {
                                targetUrl = "/admin/dashboard";
                            } else {
                                targetUrl = "/vehicles/list";
                            }
                            response.sendRedirect(targetUrl);
                        })
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .key("SuperTajnyKlicKteryNikdoNeuhodne2026") // Pevný klíč, neměnit!
                        .tokenValiditySeconds(31536000) // 60 * 60 * 24 * 365 = 1 rok
                        .alwaysRemember(true) // Tohle je trik! Nemusíš pak dávat checkbox do HTML
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                );

        return http.build();
    }
}