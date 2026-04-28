package com.pahal.billingApp.controller;

import com.pahal.billingApp.dto.LoginRequest;
import com.pahal.billingApp.entity.User;
import com.pahal.billingApp.enums.Role;
import com.pahal.billingApp.repository.UserRepository;
import com.pahal.billingApp.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

//@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> user = userRepository.findByUserId(request.getUserId());
        if (user.isPresent()) {
            User userData = user.get();
            if (passwordEncoder.matches(request.getPassword(), userData.getPassword()) && userData.getUserId().equals(request.getUserId())) {
                String userTenantId = userData.getTenantId();
                Role role  = userData.getRole();
                String token = jwtService.generateToken(request.getUserId(), userTenantId, role);
//                return new ResponseEntity<>("User login successful",HttpStatus.OK);
                return ResponseEntity.ok(Collections.singletonMap("token", token));


            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Invalid userId or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);

    }


}
