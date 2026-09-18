package com.shyblack.cryptosignals.exception;

/**
 * Raised when an external news provider cannot be reached or returns unusable data.
 */
public class NewsProviderException extends RuntimeException {

	public NewsProviderException(String message) {
		super(message);
	}

	public NewsProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}