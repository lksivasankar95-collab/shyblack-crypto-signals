package com.shyblack.cryptosignals.dto.live;

/**
 * Explicit safety confirmation from the user. Without {@code acknowledged=true}
 * the backend refuses to enable live trading.
 */
public record LiveActivateRequest(boolean acknowledged) {}
