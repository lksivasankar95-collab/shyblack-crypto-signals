package com.shyblack.cryptosignals.entity.enums;

/**
 * How an article relates to an affected asset.
 */
public enum NewsAssetRelationshipType {
	/** Asset is the primary subject of the article (usually in the title). */
	PRIMARY,
	/** Asset is mentioned in the article but not the main subject. */
	MENTIONED
}