package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.settings.NotificationPreferenceRequest;
import com.shyblack.cryptosignals.dto.settings.NotificationPreferenceResponse;
import com.shyblack.cryptosignals.exception.BadRequestException;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/notifications")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Notification preferences")
@SecurityRequirement(name = "bearer-jwt")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    @Operation(summary = "Get the authenticated user's notification preferences")
    @GetMapping
    public NotificationPreferenceResponse get() {
        return service.get(currentPrincipal());
    }

    @Operation(summary = "Update notification preferences (only non-null fields applied)")
    @PutMapping
    public NotificationPreferenceResponse update(@RequestBody NotificationPreferenceRequest request) {
        return service.update(currentPrincipal(), request);
    }

    private static UserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BadRequestException("Authentication required");
        }
        return principal;
    }
}
