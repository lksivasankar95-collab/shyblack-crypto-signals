package com.shyblack.cryptosignals.entity.enums;

import java.util.Locale;

/**
 * NFM event types (spec §5). {@link #category()} groups them; {@link #scheduled()}
 * marks events with a known release time for which an expected/actual pair and a
 * surprise value are meaningful (macro / central-bank releases).
 *
 * <p>An event type never determines direction — the market reaction does.</p>
 */
public enum NewsEventType {

	// A. MACRO
	FOMC(NewsEventCategory.MACRO, true),
	FED_RATE_DECISION(NewsEventCategory.MACRO, true),
	FED_MINUTES(NewsEventCategory.MACRO, true),
	FED_SPEECH(NewsEventCategory.MACRO, true),
	CPI(NewsEventCategory.MACRO, true),
	CORE_CPI(NewsEventCategory.MACRO, true),
	PCE(NewsEventCategory.MACRO, true),
	CORE_PCE(NewsEventCategory.MACRO, true),
	NFP(NewsEventCategory.MACRO, true),
	UNEMPLOYMENT(NewsEventCategory.MACRO, true),
	GDP(NewsEventCategory.MACRO, true),
	PPI(NewsEventCategory.MACRO, true),
	RETAIL_SALES(NewsEventCategory.MACRO, true),
	JOBLESS_CLAIMS(NewsEventCategory.MACRO, true),
	ISM(NewsEventCategory.MACRO, true),
	TREASURY_YIELD_SHOCK(NewsEventCategory.MACRO, false),
	DXY_SHOCK(NewsEventCategory.MACRO, false),

	// B. CENTRAL BANK
	ECB(NewsEventCategory.CENTRAL_BANK, true),
	BOJ(NewsEventCategory.CENTRAL_BANK, true),
	BOE(NewsEventCategory.CENTRAL_BANK, true),
	BOC(NewsEventCategory.CENTRAL_BANK, true),
	RBA(NewsEventCategory.CENTRAL_BANK, true),
	SNB(NewsEventCategory.CENTRAL_BANK, true),

	// C. CRYPTO STRUCTURAL
	BTC_ETF(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	ETH_ETF(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	ETF_FLOW(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	HALVING(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	PROTOCOL_UPGRADE(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	TOKEN_UNLOCK(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	MAJOR_LISTING(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	MAJOR_DELISTING(NewsEventCategory.CRYPTO_STRUCTURAL, false),
	STABLECOIN_EVENT(NewsEventCategory.CRYPTO_STRUCTURAL, false),

	// D. REGULATION
	SEC(NewsEventCategory.REGULATION, false),
	CFTC(NewsEventCategory.REGULATION, false),
	TREASURY(NewsEventCategory.REGULATION, false),
	CONGRESS(NewsEventCategory.REGULATION, false),
	MICA(NewsEventCategory.REGULATION, false),
	EXCHANGE_REGULATION(NewsEventCategory.REGULATION, false),
	CRYPTO_LEGISLATION(NewsEventCategory.REGULATION, false),
	SANCTIONS(NewsEventCategory.REGULATION, false),

	// E. SECURITY
	EXCHANGE_HACK(NewsEventCategory.SECURITY, false),
	PROTOCOL_HACK(NewsEventCategory.SECURITY, false),
	BRIDGE_HACK(NewsEventCategory.SECURITY, false),
	WALLET_EXPLOIT(NewsEventCategory.SECURITY, false),
	STABLECOIN_DEPEG(NewsEventCategory.SECURITY, false),
	SECURITY_BREACH(NewsEventCategory.SECURITY, false),

	// F. MARKET SHOCK
	LIQUIDATION_CASCADE(NewsEventCategory.MARKET_SHOCK, false),
	EXCHANGE_OUTAGE(NewsEventCategory.MARKET_SHOCK, false),
	BANKING_CRISIS(NewsEventCategory.MARKET_SHOCK, false),
	CREDIT_EVENT(NewsEventCategory.MARKET_SHOCK, false),
	GLOBAL_RISK_EVENT(NewsEventCategory.MARKET_SHOCK, false),
	GEOPOLITICAL_EVENT(NewsEventCategory.MARKET_SHOCK, false),
	TARIFF_EVENT(NewsEventCategory.MARKET_SHOCK, false),

	OTHER(NewsEventCategory.OTHER, false);

	private final NewsEventCategory category;
	private final boolean scheduled;

	NewsEventType(NewsEventCategory category, boolean scheduled) {
		this.category = category;
		this.scheduled = scheduled;
	}

	public NewsEventCategory category() {
		return category;
	}

	public boolean scheduled() {
		return scheduled;
	}

	public static NewsEventType parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return OTHER;
		}
		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return OTHER;
		}
	}
}
