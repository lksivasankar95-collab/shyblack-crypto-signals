package com.shyblack.cryptosignals.exception;

/**
 * Raised when a stored news article does not exist.
 */
public class NewsNotFoundException extends ResourceNotFoundException {

	public NewsNotFoundException(String message) {
		super(message);
	}
}