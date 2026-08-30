package com.shyblack.cryptosignals.exception;

public class MarketUpstreamException extends RuntimeException {

	public MarketUpstreamException(String message, Throwable cause) {
		super(message, cause);
	}

	public MarketUpstreamException(String message) {
		super(message);
	}
}
