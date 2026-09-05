package com.shyblack.cryptosignals.controller;

import com.shyblack.cryptosignals.dto.signal.SignalResponse;
import com.shyblack.cryptosignals.entity.enums.SignalStatus;
import com.shyblack.cryptosignals.entity.enums.TradingMode;
import com.shyblack.cryptosignals.service.SignalService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    /**
     * List signals.
     *
     * @param mode   optional trading mode filter (SPOT, FUTURES, OPTIONS); defaults to SPOT
     * @param status optional status filter (ACTIVE, PENDING, CLOSED, DRAFT)
     */
    @GetMapping
    public List<SignalResponse> list(
            @RequestParam(required = false, defaultValue = "SPOT") TradingMode mode,
            @RequestParam(required = false) SignalStatus status) {
        if (mode != null) {
            return signalService.findByModeAndStatus(mode, status);
        }
        return signalService.findAll();
    }

    @GetMapping("/{id}")
    public SignalResponse getById(@PathVariable UUID id) {
        return signalService.findById(id);
    }
}
