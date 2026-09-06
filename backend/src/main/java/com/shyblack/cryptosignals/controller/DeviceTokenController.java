package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.entity.DeviceToken;
import com.shyblack.cryptosignals.entity.User;
import com.shyblack.cryptosignals.repository.DeviceTokenRepository;
import com.shyblack.cryptosignals.repository.UserRepository;
import com.shyblack.cryptosignals.security.UserPrincipal;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenController.class);

    private final DeviceTokenRepository repo;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> register(@RequestBody Map<String, String> body, Authentication auth) {
        String token = body.get("token");
        if (token == null || token.isBlank()) return ResponseEntity.badRequest().build();
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        Optional<User> userOpt = userRepository.findById(p.getId());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();
        User user = userOpt.get();

        // avoid duplicates
        if (repo.existsByToken(token)) {
            log.debug("Device token already exists for token={}", token);
            return ResponseEntity.ok().build();
        }

        DeviceToken dt = new DeviceToken();
        dt.setUser(user);
        dt.setToken(token);
        dt.setActive(true);
        repo.save(dt);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<?> remove(@RequestBody Map<String, String> body, Authentication auth) {
        String token = body.get("token");
        if (token == null || token.isBlank()) return ResponseEntity.badRequest().build();
        UserPrincipal p = (UserPrincipal) auth.getPrincipal();
        Optional<User> userOpt = userRepository.findById(p.getId());
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();
        User user = userOpt.get();

        repo.findByUserAndActiveTrue(user).stream()
                .filter(dt -> token.equals(dt.getToken()))
                .forEach(dt -> {
                    dt.setActive(false);
                    repo.save(dt);
                });
        return ResponseEntity.ok().build();
    }
}
