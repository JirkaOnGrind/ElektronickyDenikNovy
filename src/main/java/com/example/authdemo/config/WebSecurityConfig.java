package com.example.authdemo.config;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${security.remember-me.key:}") String configuredRememberMeKey) throws Exception {
        String resolvedRememberMeKey = configuredRememberMeKey;
        if (resolvedRememberMeKey == null || resolvedRememberMeKey.isBlank()) {
            resolvedRememberMeKey = UUID.randomUUID().toString();
            log.warn("REMEMBER_ME_KEY není nastaven; používá se dočasný klíč platný pouze do restartu aplikace.");
        }
        final String rememberMeKey = resolvedRememberMeKey;

        http
                .csrf(csrf -> csrf
                        // Mobilní offline worker neposílá CSRF token. Tento endpoint
                        // zůstává oddělený; jeho autentizace je samostatný auditní bod.
                        .ignoringRequestMatchers("/api/sync/**"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline' 'unsafe-eval' "
                                        + "https://cdn.tailwindcss.com https://cdnjs.cloudflare.com; "
                                        + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                        + "font-src 'self' https://fonts.gstatic.com data:; "
                                        + "img-src 'self' data:; connect-src 'self'; "
                                        + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; "
                                        + "form-action 'self'; upgrade-insecure-requests"))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Referrer-Policy", "strict-origin-when-cross-origin"))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/register",
                                "/register/company",
                                "/verification",
                                "/changePassword",
                                "/newPassword",
                                "/privacy",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()
                        .requestMatchers(
                                "/auth/registerUser",
                                "/auth/registerCompany",
                                "/auth/send-verification",
                                "/auth/new-password",
                                "/auth/verification",
                                "/auth/verification/sendAgain"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()

                        // Zachováno kvůli existujícímu offline klientovi. Controller
                        // musí odmítat anonymní nebo neoprávněné požadavky.
                        .requestMatchers("/api/sync/**").authenticated()
                        .requestMatchers("/api/offline-vehicles").authenticated()

                        .requestMatchers("/super-admin/**", "/superadmin/**").hasRole("SUPER_ADMIN")

                        // Správu oprávnění konkrétního stroje může používat i jeho správce.
                        .requestMatchers("/admin/vehicles/**")
                        .access(new WebExpressionAuthorizationManager("isAuthenticated() and !hasRole('MAINTENANCE')"))
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "OWNER", "SUPER_ADMIN")

                        .requestMatchers("/maintenance", "/maintenance/**", "/revision", "/revision/**")
                        .authenticated()

                        .requestMatchers("/vehicles/register").hasAnyRole("ADMIN", "OWNER", "SUPER_ADMIN")
                        .requestMatchers("/vehicles/**").hasAnyRole("ADMIN", "OWNER", "USER", "MAINTENANCE", "SUPER_ADMIN")

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
                        .key(rememberMeKey)
                        .tokenValiditySeconds(2592000)
                        .alwaysRemember(true)
                        .useSecureCookie(true)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll()
                );

        return http.build();
    }
}
