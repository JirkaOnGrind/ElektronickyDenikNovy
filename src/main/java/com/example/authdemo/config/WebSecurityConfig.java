package com.example.authdemo.config;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12 is intentionally explicit so every newly stored password uses
        // the same adaptive, salted one-way hash.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public CookieSameSiteSupplier rememberMeCookieSameSiteSupplier() {
        return CookieSameSiteSupplier.of(SameSite.STRICT)
                .whenHasName("__Host-REMEMBER-ME");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${security.remember-me.key:}") String configuredRememberMeKey,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) throws Exception {
        String resolvedRememberMeKey = configuredRememberMeKey;
        if (resolvedRememberMeKey == null || resolvedRememberMeKey.isBlank()) {
            resolvedRememberMeKey = UUID.randomUUID().toString();
            log.warn("REMEMBER_ME_KEY není nastaven; používá se dočasný klíč platný pouze do restartu aplikace.");
        }
        final String rememberMeKey = resolvedRememberMeKey;
        final String rememberMeCookieName = secureCookies ? "__Host-REMEMBER-ME" : "remember-me";

        http
                // CSRF remains enabled for every state-changing endpoint, including
                // the offline sync API. The client obtains a token from /api/csrf.
                .csrf(csrf -> {})
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline' "
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
                        .requestMatchers(HttpMethod.GET, "/api/csrf").authenticated()

                        // Zachováno kvůli existujícímu offline klientovi. Controller
                        // musí odmítat anonymní nebo neoprávněné požadavky.
                        .requestMatchers("/api/sync/**").authenticated()
                        .requestMatchers("/api/offline-vehicles").authenticated()

                        .requestMatchers("/super-admin/**", "/superadmin/**").hasRole("SUPER_ADMIN")

                        .requestMatchers(HttpMethod.GET, "/admin/vehicles/*/users").authenticated()
                        .requestMatchers(HttpMethod.POST, "/admin/vehicles/*/permissions").authenticated()
                        .requestMatchers("/admin/vehicles/**").hasAnyRole("ADMIN", "OWNER", "SUPER_ADMIN")
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
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false))
                .rememberMe(remember -> remember
                        .key(rememberMeKey)
                        .rememberMeCookieName(rememberMeCookieName)
                        .tokenValiditySeconds(2592000)
                        .alwaysRemember(true)
                        .useSecureCookie(secureCookies)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .deleteCookies("JSESSIONID", "remember-me", "__Host-JSESSIONID", "__Host-REMEMBER-ME")
                        .permitAll()
                );

        return http.build();
    }
}
