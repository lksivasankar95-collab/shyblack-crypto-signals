package com.shyblack.cryptosignals.dto.news;

import java.util.List;

/**
 * Uniform pagination wrapper used by the news APIs.
 *
 * @param <T> item type
 */
public record PageResponse<T>(
		List<T> items,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean hasNext
) {
}