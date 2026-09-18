package com.shyblack.cryptosignals.dto.news;

import com.shyblack.cryptosignals.entity.enums.NewsAssetRelationshipType;

public record NewsAssetResponse(
		String symbol,
		String name,
		Double relevanceScore,
		NewsAssetRelationshipType relationshipType
) {
}