package com.shyblack.cryptosignals.exchange;

/**
 * Wraps failures from exchange adapters (Binance HTTP, HMAC, JSON parse).
 * `retryable` is true only for transport-level failures — never for order
 * submission errors where retry would risk a duplicate order.
 */
public class ExchangeAdapterException extends RuntimeException {

	private final boolean retryable;
	private final Integer httpStatus;
	private final Integer exchangeCode;

	public ExchangeAdapterException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
		this.httpStatus = null;
		this.exchangeCode = null;
	}

	public ExchangeAdapterException(String message, Throwable cause, boolean retryable,
			Integer httpStatus, Integer exchangeCode) {
		super(message, cause);
		this.retryable = retryable;
		this.httpStatus = httpStatus;
		this.exchangeCode = exchangeCode;
	}

	public boolean retryable() { return retryable; }
	public Integer httpStatus() { return httpStatus; }
	public Integer exchangeCode() { return exchangeCode; }
}
