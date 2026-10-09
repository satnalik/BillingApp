package com.pahal.billingApp.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.pahal.billingApp.service.UserService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/** UI routing and launcher control exist only in the local Windows profile. */
@Controller
@Profile("desktop")
public class DesktopController {
    private final ConfigurableApplicationContext context;
    private final byte[] controlToken;
    private final UserService userService;
    private final com.pahal.billingApp.licensing.TenantLicenseService licenses;
    private final AtomicBoolean stopping = new AtomicBoolean();

    public DesktopController(ConfigurableApplicationContext context,
                             UserService userService,
                             com.pahal.billingApp.licensing.TenantLicenseService licenses,
                             @Value("${app.desktop.control-token}") String controlToken) {
        if (controlToken.length() < 32) {
            throw new IllegalArgumentException("A private desktop control token is required");
        }
        this.context = context;
        this.userService = userService;
        this.licenses = licenses;
        this.controlToken = controlToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping({"/", "/login", "/dashboard", "/customers", "/bill/**", "/masters/**",
            "/purchases", "/purchases/**", "/reports", "/barcode-labels", "/accounts",
            "/settings", "/settings/**", "/access", "/access/**", "/new-bill", "/get-bill",
            "/tenant-settings", "/add-products", "/add-salesman", "/suppliers", "/users/new"})
    public String application() {
        return "forward:/index.html";
    }

    @GetMapping("/desktop/ready")
    @ResponseBody
    public ResponseEntity<String> ready(HttpServletRequest request) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (stopping.get() || !licenses.ready()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok("pahal-retail-ready");
    }

    @GetMapping("/desktop/setup/status")
    @ResponseBody
    public ResponseEntity<?> setupStatus(HttpServletRequest request) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(java.util.Map.of("setupRequired", userService.isDesktopFirstStoreSetupRequired(),
                "deploymentId", licenses.installationId(), "signingKeyConfigured", licenses.installation().get("signingKeyConfigured")));
    }

    @PostMapping("/desktop/shutdown")
    @ResponseBody
    public ResponseEntity<Void> shutdown(HttpServletRequest request) {
        if (!authorized(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (stopping.compareAndSet(false, true)) {
            Thread shutdown = new Thread(() -> {
                try {
                    // Allow the response to complete before draining requests and closing the pool.
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                System.exit(SpringApplication.exit(context, () -> 0));
            }, "pahal-desktop-shutdown");
            shutdown.setDaemon(false);
            shutdown.start();
        }
        return ResponseEntity.accepted().build();
    }

    private boolean authorized(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String supplied = request.getHeader("X-Desktop-Token");
        return ("127.0.0.1".equals(remote) || "::1".equals(remote))
                && (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.POST.matches(request.getMethod()))
                && supplied != null
                && MessageDigest.isEqual(controlToken, supplied.getBytes(StandardCharsets.UTF_8));
    }
}
