package com.shyblack.cryptosignals.service.nfm;

import com.shyblack.cryptosignals.entity.enums.NewsCategory;
import com.shyblack.cryptosignals.entity.enums.NewsEventStage;
import com.shyblack.cryptosignals.entity.enums.NewsEventType;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Maps an existing {@link NewsCategory} + headline keywords to an NFM
 * {@link NewsEventType} and {@link NewsEventStage}. Deterministic, no ML.
 */
@Component
public class NewsEventTypeClassifier {

	public NewsEventType classify(NewsCategory category, String title, String summary) {
		String text = ((title == null ? "" : title) + " " + (summary == null ? "" : summary))
				.toLowerCase(Locale.ROOT);
		if (category == null) {
			return NewsEventType.OTHER;
		}
		return switch (category) {
			case ETF -> classifyEtf(text);
			case HACK, EXPLOIT, SECURITY -> classifySecurity(text);
			case REGULATION, LEGAL -> classifyRegulation(text);
			case LISTING -> NewsEventType.MAJOR_LISTING;
			case DELISTING -> NewsEventType.MAJOR_DELISTING;
			case TOKEN_UNLOCK -> NewsEventType.TOKEN_UNLOCK;
			case PROTOCOL_UPDATE, TECHNOLOGY, NETWORK, GOVERNANCE -> NewsEventType.PROTOCOL_UPGRADE;
			case MACRO -> classifyMacro(text);
			case MARKET -> contains(text, "liquidat") ? NewsEventType.LIQUIDATION_CASCADE : NewsEventType.OTHER;
			case EXCHANGE -> contains(text, "outage") || contains(text, "halt")
					? NewsEventType.EXCHANGE_OUTAGE : NewsEventType.EXCHANGE_REGULATION;
			case FUNDING, INVESTMENT -> NewsEventType.ETF_FLOW;
			default -> NewsEventType.OTHER;
		};
	}

	public NewsEventStage inferStage(String title, String summary) {
		String text = ((title == null ? "" : title) + " " + (summary == null ? "" : summary))
				.toLowerCase(Locale.ROOT);
		if (contains(text, "approv")) return NewsEventStage.APPROVAL;
		if (contains(text, "launch") || contains(text, "goes live")) return NewsEventStage.LAUNCH;
		if (contains(text, "inflow") || contains(text, "outflow") || contains(text, "flow")) return NewsEventStage.FLOW;
		if (contains(text, "rumor") || contains(text, "reportedly") || contains(text, "unconfirmed")) {
			return NewsEventStage.RUMOR;
		}
		if (contains(text, "expect") || contains(text, "forecast") || contains(text, "preview")) {
			return NewsEventStage.EXPECTATION;
		}
		return NewsEventStage.REPORT;
	}

	private NewsEventType classifyEtf(String text) {
		if (contains(text, "eth") || contains(text, "ethereum")) return NewsEventType.ETH_ETF;
		if (contains(text, "btc") || contains(text, "bitcoin")) return NewsEventType.BTC_ETF;
		if (contains(text, "flow") || contains(text, "inflow") || contains(text, "outflow")) {
			return NewsEventType.ETF_FLOW;
		}
		return NewsEventType.BTC_ETF;
	}

	private NewsEventType classifySecurity(String text) {
		if (contains(text, "bridge")) return NewsEventType.BRIDGE_HACK;
		if (contains(text, "wallet")) return NewsEventType.WALLET_EXPLOIT;
		if (contains(text, "exchange") || contains(text, "binance") || contains(text, "coinbase")) {
			return NewsEventType.EXCHANGE_HACK;
		}
		return NewsEventType.PROTOCOL_HACK;
	}

	private NewsEventType classifyRegulation(String text) {
		if (contains(text, "sec ") || contains(text, "sec,") || contains(text, "securities and exchange")) {
			return NewsEventType.SEC;
		}
		if (contains(text, "cftc")) return NewsEventType.CFTC;
		return NewsEventType.EXCHANGE_REGULATION;
	}

	private NewsEventType classifyMacro(String text) {
		if (contains(text, "cpi")) return NewsEventType.CPI;
		if (contains(text, "fomc")) return NewsEventType.FOMC;
		if (contains(text, "fed") && contains(text, "rate")) return NewsEventType.FED_RATE_DECISION;
		if (contains(text, "nonfarm") || contains(text, "non-farm") || contains(text, "jobs report")) {
			return NewsEventType.NFP;
		}
		if (contains(text, "inflation")) return NewsEventType.CPI;
		return NewsEventType.OTHER;
	}

	private static boolean contains(String text, String needle) {
		return text.contains(needle);
	}
}
