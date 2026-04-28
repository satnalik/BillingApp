package com.pahal.billingApp.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // No @Autowired needed here in Spring 4.3+
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable) // Disable for development
//                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/adduser"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll() // ALLOW OPTIONS
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/adduser").permitAll()
                        .requestMatchers("/api/bills/**").hasAnyRole("CASHIER", "ADMIN")
                        .requestMatchers("/api/products/search").hasAnyRole("CASHIER", "ADMIN")
                        .requestMatchers("/api/salesman/addsalesman").hasAnyRole("ADMIN")// Allow Login/Signup
                        .requestMatchers("/api/salesman").hasAnyRole("CASHIER", "ADMIN")// Allow Login/Signup
                        .requestMatchers("/api/reports/**").hasAnyRole("CASHIER", "ADMIN")
                        .anyRequest().authenticated()

                );



        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers("/api/adduser") // Bypass your signup endpoint
                .requestMatchers("/error");
    }
}
