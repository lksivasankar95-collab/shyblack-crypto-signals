package com.shyblack.cryptosignals.entity.enums;

/**
 * Controlled internal news category.
 * <p>Provider-supplied categories are normalized into these values by the
 * category classifier; providers are never trusted verbatim.</p>
 */
public enum NewsCategory {
	REGULATION,
	ETF,
	EXCHANGE,
	LISTING,
	DELISTING,
	PARTNERSHIP,
	ADOPTION,
	TECHNOLOGY,
	NETWORK,
	SECURITY,
	HACK,
	EXPLOIT,
	FUNDING,
	INVESTMENT,
	TOKEN_UNLOCK,
	TOKEN_BURN,
	GOVERNANCE,
	PROTOCOL_UPDATE,
	DEFI,
	NFT,
	MACRO,
	MARKET,
	MINING,
	LEGAL,
	OTHER
}