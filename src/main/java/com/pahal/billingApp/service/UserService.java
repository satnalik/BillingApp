package com.pahal.billingApp.service;

import com.pahal.billingApp.dto.UserDetailsDTO;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Lazy
    private PasswordEncoder passwordEncoder;


    public UserDetailsDTO addUser(User user) {
        // 1. Hash the password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 2. Save the Entity (this is where the 'S' bound error happens if types mismatch)
        User savedUser = userRepository.save(user);

        // 3. Map Entity to DTO
        UserDetailsDTO dto = new UserDetailsDTO();
        dto.setId(savedUser.getId());
        dto.setUserId(savedUser.getUserId());
        dto.setRole(savedUser.getRole());

        return dto;
    }
}
