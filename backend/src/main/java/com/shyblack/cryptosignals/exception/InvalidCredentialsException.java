package com.shyblack.cryptosignals.exception;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid email or password");
	}
}
