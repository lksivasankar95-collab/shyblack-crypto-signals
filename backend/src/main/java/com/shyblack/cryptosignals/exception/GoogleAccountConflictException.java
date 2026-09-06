package com.shyblack.cryptosignals.exception;

public class GoogleAccountConflictException extends RuntimeException {

	public GoogleAccountConflictException(String email) {
		super("A password account already exists for " + email + ". Sign in with email and password instead.");
	}
}