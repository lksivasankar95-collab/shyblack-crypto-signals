package com.shyblack.cryptosignals.service;

import com.shyblack.cryptosignals.dto.settings.ConnectionValidationStatus;
import com.shyblack.cryptosignals.exchange.ExchangeAdapterException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/**
 * Maps any failure during credential validation onto a deterministic
 * {@link ConnectionValidationStatus} and a safe, human-readable message.
 *
 * <p>Three problems this exists to solve, all observed in the pre-fix probe:
 *
 * <ol>
 *   <li><b>Unclassified escapes.</b> Only {@link ExchangeAdapterException} was caught, so a
 *       non-JSON gateway response escaped as a raw {@code JsonSyntaxException} and became an
 *       HTTP 500. Every failure class now lands here.</li>
 *   <li><b>Leaked exchange payloads.</b> Spring's {@code HttpStatusCodeException.getMessage()}
 *       embeds the entire response body, which was previously truncated and returned verbatim as
 *       the user-facing message. Messages here are authored here; no exchange text is echoed.</li>
 *   <li><b>Indistinguishable causes.</b> Every failure collapsed to {@code FAILED}. A caller
 *       cannot tell a wrong key from a timeout from a ban.</li>
 * </ol>
 *
 * <p>Never returns or embeds an API key, secret, decrypted value, signature, authorization header
 * or raw exchange response body.
 */
@Component
public class ExchangeConnectionClassifier {

	/** Binance "API-key format invalid", "invalid API-key", "signature for this request is not valid". */
	private static final List<Integer> AUTH_CODES = List.of(-2014, -2015, -1022);

	/** Binance "Timestamp for this request is outside of the recvWindow." */
	private static final int TIMESTAMP_OUT_OF_WINDOW = -1021;

	/** Binance "Too many requests" / IP ban. */
	private static final int TOO_MANY_REQUESTS = -1003;

	/** HTTP 429 rate limit, HTTP 418 auto-banned IP. */
	private static final int HTTP_TOO_MANY_REQUESTS = 429;
	private static final int HTTP_IP_BANNED = 418;

	/** Outcome of a classification: a status plus a message safe to return to a client. */
	public record Classification(ConnectionValidationStatus status, String message) {
	}

	/**
	 * Classifies a failure thrown while validating a credential.
	 *
	 * <p>Order matters. Transport causes are inspected before the general adapter exception,
	 * because a read timeout is also flagged {@code retryable} and would otherwise be reported as
	 * a generic server error.
	 */
	public Classification classify(Throwable thrown) {
		if (thrown == null) {
			return new Classification(ConnectionValidationStatus.UNKNOWN_ERROR, unknownMessage());
		}

		// Transport: the request never produced an exchange answer.
		if (thrown instanceof ResourceAccessException || hasCause(thrown, SocketTimeoutException.class)) {
			return new Classification(ConnectionValidationStatus.TIMEOUT,
					"The exchange did not respond in time. The credentials were not verified; "
							+ "try again.");
		}
		if (thrown instanceof UnknownHostException || hasCause(thrown, UnknownHostException.class)
				|| hasCause(thrown, java.net.ConnectException.class)) {
			return new Classification(ConnectionValidationStatus.NETWORK_ERROR,
					"The exchange could not be reached. Check network connectivity and try again.");
		}

		// Malformed or unexpected payloads: not the credential's fault and not classifiable.
		if (thrown instanceof com.google.gson.JsonParseException
				|| hasCause(thrown, com.google.gson.JsonParseException.class)) {
			return new Classification(ConnectionValidationStatus.UNKNOWN_ERROR,
					"The exchange returned a response that could not be read, so the "
							+ "credentials were not verified.");
		}

		if (thrown instanceof ExchangeAdapterException adapterFailure) {
			return classifyAdapterFailure(adapterFailure);
		}

		// A storage fault is ours, not the credential's.
		if (thrown instanceof DataAccessException) {
			return new Classification(ConnectionValidationStatus.UNKNOWN_ERROR,
					"The connection test could not be recorded. Try again.");
		}

		return new Classification(ConnectionValidationStatus.UNKNOWN_ERROR, unknownMessage());
	}

	private Classification classifyAdapterFailure(ExchangeAdapterException failure) {
		Integer code = failure.exchangeCode();
		Integer status = failure.httpStatus();

		if (code != null && AUTH_CODES.contains(code)) {
			// -1022 is a signature failure, which in practice means a wrong API secret rather
			// than a wrong key. It gets its own status so the user is told which half is wrong.
			return code == -1022
					? new Classification(ConnectionValidationStatus.INVALID_SIGNATURE,
							"Binance rejected the API signature. Check the API secret.")
					: new Classification(ConnectionValidationStatus.INVALID_CREDENTIALS,
							"Binance rejected the provided API credentials. Check the API key, "
									+ "its permissions and the permitted IP address.");
		}

		if (code != null && code == TIMESTAMP_OUT_OF_WINDOW) {
			return new Classification(ConnectionValidationStatus.TIMESTAMP_ERROR,
					"Binance rejected the request timestamp. Check the system clock and the "
							+ "configured receive window.");
		}

		if (code != null && code == TOO_MANY_REQUESTS
				|| status != null && status == HTTP_TOO_MANY_REQUESTS
				|| status != null && status == HTTP_IP_BANNED) {
			return new Classification(ConnectionValidationStatus.RATE_LIMITED,
					"Binance is rate limiting this request, or the IP is temporarily banned. "
							+ "Wait before testing again.");
		}

		// A 200 that still failed parsing, or an unlabelled 4xx: the credential is fine, the
		// exchange simply did not give us the data we asked for.
		if (status != null && status == 200) {
			return new Classification(ConnectionValidationStatus.UNKNOWN_ERROR,
					"The exchange answered but did not return readable account data, so the "
							+ "credentials were not verified.");
		}

		if (status != null && status >= 500) {
			return new Classification(ConnectionValidationStatus.BINANCE_API_ERROR,
					"Binance reported a server-side error. The credentials were not verified; "
							+ "try again shortly.");
		}

		if (status != null && (status == 401 || status == 403)) {
			// No usable Binance code alongside the 401/403. Treat as a credential problem rather
			// than reporting a generic client error for what is almost always auth.
			return new Classification(ConnectionValidationStatus.INVALID_CREDENTIALS,
					"Binance rejected the provided API credentials. Check the API key, its "
							+ "permissions and the permitted IP address.");
		}

		if (status != null && status >= 400) {
			return new Classification(ConnectionValidationStatus.BINANCE_CLIENT_ERROR,
					"Binance rejected the request. Check the API key permissions and the "
							+ "configured account type.");
		}

		return new Classification(ConnectionValidationStatus.UNKNOWN_ERROR, unknownMessage());
	}

	private static String unknownMessage() {
		return "The connection test could not be completed. The credentials were not verified.";
	}

	private static boolean hasCause(Throwable thrown, Class<? extends Throwable> type) {
		Throwable current = thrown;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}