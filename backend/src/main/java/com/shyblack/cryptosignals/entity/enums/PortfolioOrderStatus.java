package com.shyblack.cryptosignals.entity.enums;

/**
 * Exchange-confirmed order state as reported by a read model.
 *
 * <p>This enum carries only states the exchange itself reports on an order-read endpoint. It
 * deliberately does <em>not</em> carry the local submission states {@code CREATED}, {@code
 * SUBMITTING} or {@code SUBMITTED}: those are pre-exchange intentions tracked by
 * {@link LiveOrderStatus} in the execution module, and publishing them from a read endpoint would
 * let a submitted-but-unconfirmed order look like exchange state. Keeping them out is what makes
 * "submitted" and "filled" impossible to conflate here.
 *
 * <p>{@link #UNKNOWN} is the default and the only safe fallback. A transport read maps to UNKNOWN,
 * never to FILLED. In particular an HTTP 200 response is <em>not</em> FILLED: it means the exchange
 * answered, and the status field decides what the order actually is.
 */
public enum PortfolioOrderStatus {

	/** Accepted by the exchange and resting on the book, nothing filled yet. */
	NEW,

	/** On the book with part of the quantity filled. */
	PARTIALLY_FILLED,

	/** Fully filled. Only ever reached from explicit exchange evidence. */
	FILLED,

	/** Removed from the book without being fully filled. */
	CANCELED,

	/** Refused by the exchange. */
	REJECTED,

	/** Removed from the book because its time in force elapsed. */
	EXPIRED,

	/**
	 * The exchange state could not be determined. An order in this state must be reconciled before
	 * any retry, and must never be displayed as filled, closed or successful.
	 */
	UNKNOWN;

	/**
	 * Maps a raw exchange status string.
	 *
	 * <p>Only a value the exchange actually reported is mapped. {@code PENDING_CANCEL} and
	 * {@code PENDING_CANCEL_REJECTED} map to {@link #CANCELED} because the exchange considers the
	 * order off the book in that state; a deliberately cancelled-before-fill order and a
	 * cancel-in-flight order are the same fact for a read view. Anything absent, blank or
	 * unrecognised is {@link #UNKNOWN}.
	 */
	public static PortfolioOrderStatus fromExchange(String raw) {
		if (raw == null || raw.isBlank()) {
			return UNKNOWN;
		}
		return switch (raw.trim().toUpperCase()) {
			case "NEW" -> NEW;
			case "PARTIALLY_FILLED" -> PARTIALLY_FILLED;
			case "FILLED" -> FILLED;
			case "CANCELED", "CANCELLED", "PENDING_CANCEL", "PENDING_CANCEL_REJECTED" -> CANCELED;
			case "REJECTED" -> REJECTED;
			case "EXPIRED", "EXPIRED_IN_MATCH" -> EXPIRED;
			default -> UNKNOWN;
		};
	}

	/** True only for a fully filled order, i.e. when the exchange said so explicitly. */
	public boolean isFilled() {
		return this == FILLED;
	}

	/**
	 * True when the order is still live on the exchange book: resting, or partly filled.
	 * A cancelled, rejected, expired or undetermined order is not open.
	 */
	public boolean isOpen() {
		return this == NEW || this == PARTIALLY_FILLED;
	}
}