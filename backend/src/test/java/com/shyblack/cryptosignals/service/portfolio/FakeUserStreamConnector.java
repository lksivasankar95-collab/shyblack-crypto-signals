package com.shyblack.cryptosignals.service.portfolio;

import com.shyblack.cryptosignals.service.portfolio.UserStreamConnector.Handle;
import com.shyblack.cryptosignals.service.portfolio.UserStreamConnector.Request;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

/**
 * In-process transport double for user-data streams.
 *
 * <p>No socket is opened and no exchange is contacted. It exists so a lifecycle test can prove
 * the things that actually carry the risk — that a stream is opened once and only after a REST
 * baseline, that a drop triggers reconciliation and a fresh stream, that one user's scope is
 * never touched by another's — without any network at all.
 *
 * <p>Deliberately records <em>every</em> {@link #open(Request)} call rather than deduplicating.
 * The real connector is idempotent per scope and so is {@link LiveUserStreamManager}; a double
 * that silently deduplicated would hide a duplicate-lifecycle bug instead of exposing it.
 */
public final class FakeUserStreamConnector implements UserStreamConnector {

	private final Map<String, Request> requests = new ConcurrentHashMap<>();
	private final List<String> openOrder = new CopyOnWriteArrayList<>();
	private final AtomicInteger opens = new AtomicInteger();

	/**
	 * Mirrors the real connector's once-per-disconnect guarantee.
	 *
	 * <p>{@code BinanceUserStreamConnector} signals a drop through a compare-and-set latch, so one
	 * disconnect fires the callback exactly once no matter how many close or error signals the
	 * transport produces. Without that latch here, a test that fires the callback repeatedly would
	 * be testing a transport bug the real one does not have.
	 */
	private final Map<String, AtomicBoolean> dropSignalled = new ConcurrentHashMap<>();

	@Override
	public Handle open(Request request) {
		String key = key(request);
		opens.incrementAndGet();
		openOrder.add(key);
		requests.put(key, request);
		// A fresh connection gets a fresh latch, exactly as the connector resets on reconnect.
		dropSignalled.put(key, new AtomicBoolean(false));

		AtomicBoolean open = new AtomicBoolean(true);
		return new Handle() {
			@Override
			public boolean isOpen() {
				return open.get();
			}

			@Override
			public void close() {
				open.set(false);
				requests.remove(key);
			}
		};
	}

	/** Number of {@code open} calls. More than one per scope means a duplicate lifecycle. */
	public int openCount() {
		return opens.get();
	}

	/** Every scope that was opened, in order, so a duplicate is visible and not just a count. */
	public List<String> openedScopes() {
		return List.copyOf(openOrder);
	}

	/** Scopes with a live handle. */
	public Set<String> liveScopes() {
		return Set.copyOf(requests.keySet());
	}

	public boolean isLive(java.util.UUID userId, com.shyblack.cryptosignals.entity.enums.AccountCategory category) {
		return requests.containsKey(userId + ":" + category.name());
	}

	/** The request recorded for a scope, for injecting a payload or a drop. */
	public Request requestFor(
			java.util.UUID userId, com.shyblack.cryptosignals.entity.enums.AccountCategory category) {
		return requests.get(userId + ":" + category.name());
	}

	/** Delivers a raw payload into the scope's sink, exactly as the transport would. */
	public void emit(
			java.util.UUID userId,
			com.shyblack.cryptosignals.entity.enums.AccountCategory category,
			String rawJson) {
		Request request = requestFor(userId, category);
		if (request == null) {
			throw new IllegalStateException("No stream registered for " + userId + ":" + category);
		}
		request.payloadSink().accept(rawJson);
	}

	/** Simulates a transport-level disconnect, firing the drop callback exactly once. */
	public void drop(
			java.util.UUID userId, com.shyblack.cryptosignals.entity.enums.AccountCategory category) {
		String key = userId + ":" + category.name();
		Request request = requests.get(key);
		if (request == null) {
			throw new IllegalStateException("No stream registered for " + key);
		}
		requests.remove(key);
		AtomicBoolean latch = dropSignalled.get(key);
		if (latch != null && latch.compareAndSet(false, true)) {
			request.onDisconnected().run();
		}
	}

	/**
	 * Fires the drop callback as many times as asked, bypassing the once-only latch.
	 *
	 * <p>Exists to model a transport that misbehaves, so a test can prove the manager does not
	 * amplify such a signal into repeated exchange calls.
	 */
	public void dropRepeatedly(
			java.util.UUID userId,
			com.shyblack.cryptosignals.entity.enums.AccountCategory category,
			int times) {
		Request request = requestFor(userId, category);
		if (request == null) {
			throw new IllegalStateException("No stream registered for " + userId + ":" + category);
		}
		for (int i = 0; i < times; i++) {
			request.onDisconnected().run();
		}
	}

	/** Clears recorded state so one test's opens cannot be attributed to the next. */
	public void reset() {
		requests.clear();
		dropSignalled.clear();
		openOrder.clear();
		opens.set(0);
	}

	private static String key(Request request) {
		return request.userId() + ":" + request.accountCategory().name();
	}
}