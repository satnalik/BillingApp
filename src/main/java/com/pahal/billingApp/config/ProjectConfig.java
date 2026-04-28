package com.pahal.billingApp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ProjectConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // This tells Spring: "Whenever someone needs a PasswordEncoder,
        // give them a BCryptPasswordEncoder."
        return new BCryptPasswordEncoder();
    }
}