package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.LoginRequest;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.repository.UserRepository;
import com.pahal.billingApp.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(request.getEmail());
        if (user.isPresent()) {
            User userData = user.get();
            if (userData.getPassword().equals(request.getPassword()) && userData.getEmail().equals(request.getEmail())) {
                String userTenantId = userData.getTenantId();
                jwtService.generateToken(request.getEmail(), userTenantId);
                return new ResponseEntity<>("User login successful",HttpStatus.OK);


            }
        }
        return new ResponseEntity<>("Invalid Email or Password", HttpStatus.NOT_FOUND);

    }


}
