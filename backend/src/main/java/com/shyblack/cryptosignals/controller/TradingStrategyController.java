package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.strategy.*;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.security.UserPrincipal;
import com.shyblack.cryptosignals.service.strategy.TradingStrategyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/strategies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
public class TradingStrategyController {

    private final TradingStrategyService service;

    @GetMapping
    public List<TradingStrategyResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "SPOT") TradingMode mode) {
        return service.listForMode(principal, mode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradingStrategyResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateStrategyRequest req) {
        return service.create(principal, req);
    }

    @GetMapping("/{id}")
    public TradingStrategyResponse getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return service.getById(principal, id);
    }

    @PutMapping("/{id}")
    public TradingStrategyResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody UpdateStrategyRequest req) {
        return service.update(principal, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        service.delete(principal, id);
    }

    @GetMapping("/active")
    public ActiveStrategyResponse getActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "SPOT") TradingMode mode) {
        return service.getActive(principal, mode);
    }

    @PostMapping("/active")
    public ActiveStrategyResponse setActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SetActiveStrategyRequest req) {
        return service.setActive(principal, req);
    }
}
