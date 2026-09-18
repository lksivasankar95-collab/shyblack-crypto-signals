package com.shyblack.cryptosignals.exception;

/**
 * Raised when a news filter combination or value is invalid.
 */
public class InvalidNewsFilterException extends BadRequestException {

	public InvalidNewsFilterException(String message) {
		super(message);
	}
}