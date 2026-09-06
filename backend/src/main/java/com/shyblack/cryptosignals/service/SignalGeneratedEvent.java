package com.shyblack.cryptosignals.service;

import java.util.UUID;

public class SignalGeneratedEvent {
    private final UUID signalId;

    public SignalGeneratedEvent(UUID signalId) {
        this.signalId = signalId;
    }

    public UUID getSignalId() {
        return signalId;
    }
}
