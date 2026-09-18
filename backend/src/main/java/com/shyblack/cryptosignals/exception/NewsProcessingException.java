package com.shyblack.cryptosignals.exception;

/**
 * Raised when the news classification/scoring pipeline fails for an article.
 */
public class NewsProcessingException extends RuntimeException {

	public NewsProcessingException(String message) {
		super(message);
	}

	public NewsProcessingException(String message, Throwable cause) {
		super(message, cause);
	}
}