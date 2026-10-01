package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.entity.ExchangeCredential;
import com.shyblack.cryptosignals.entity.enums.AccountCategory;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Transport seam for a Binance user-data stream.
 *
 * <p>Kept as an interface so the lifecycle manager can be exercised deterministically without
 * opening a socket, while the production implementation owns listen-key handling and reconnect
 * backoff. Implementations must deliver raw payloads verbatim and must never throw into the
 * manager for an individual message: a malformed payload is the processor's concern, not the
 * transport's.
 */
public interface UserStreamConnector {

	/**
	 * A single open stream.
	 */
	interface Handle extends AutoCloseable {

		/** True while the underlying socket is usable. */
		boolean isOpen();

		/** Idempotent; safe to call repeatedly and from a shutdown hook. */
		@Override
	void close();
	}

	/** Everything needed to open one scope's stream. */
	record Request(
			UUID userId,
			AccountCategory accountCategory,
			ExchangeCredential credential,
			/** Receives each raw payload delivered by the exchange. */
			Consumer<String> payloadSink,
			/**
			 * Invoked when the stream drops for any reason. The manager reconciles over REST before
			 * resuming, because events emitted while disconnected are assumed lost rather than
			 * assumed irrelevant.
			 */
			Runnable onDisconnected) {}

	/**
	 * Opens a stream. Implementations must be idempotent per {@code (userId, accountCategory)}: a
	 * second call for a scope that already has a live stream must not create a second connection.
	 */
	Handle open(Request request);
}