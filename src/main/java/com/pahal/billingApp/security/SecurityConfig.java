package com.pahal.billingApp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final boolean desktop;

    // No @Autowired needed here in Spring 4.3+
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.desktop = environment.acceptsProfiles(Profiles.of("desktop"));
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable) // API authentication uses bearer tokens; no cookie-based sessions are used.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll() // ALLOW OPTIONS
                        .requestMatchers("/error").permitAll()
                        // The desktop bundle serves the React application on the same origin.
                        // API authorization remains below; desktop control validates its own launch token.
                        .requestMatchers(request -> desktop
                                && !request.getServletPath().startsWith("/api/")
                                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())))
                        .permitAll()
                        .requestMatchers(request -> desktop && request.getServletPath().startsWith("/desktop/"))
                        .permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/provisioning/tenants/*/owner").permitAll() // Controller validates the operator-only provisioning key.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/provisioning/installation").permitAll()
                        .requestMatchers("/api/auth/change-password").authenticated()
                        .requestMatchers("/api/me/capabilities").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/license/counters").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/license/**", "/api/license").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/gst/settings").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/gst/quote").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/gst/settings", "/api/gst/periods").hasRole("ADMIN")
                        .requestMatchers("/api/gst/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/store-profile").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/store-profile/**").hasRole("ADMIN")
                        .requestMatchers("/api/shifts/**").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/bills/**").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/products/**").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/products/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/products/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/products/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/products/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/purchases/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/inventory/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/suppliers/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/customers/**").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/salesman/**").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/salesman/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/reports/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/dashboard/**").hasAnyRole("CASHIER", "MANAGER", "ADMIN")
                        .anyRequest().authenticated()

                );



        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }
}
