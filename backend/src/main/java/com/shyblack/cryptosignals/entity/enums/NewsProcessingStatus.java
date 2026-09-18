package com.shyblack.cryptosignals.entity.enums;

/**
 * Pipeline status of a stored news article.
 */
public enum NewsProcessingStatus {
	/** Article was persisted but classification is not finished. */
	NEW,
	/** Article was fully normalized, classified, scored and persisted. */
	PROCESSED,
	/** Article could not be processed reliably and was preserved as-is. */
	PARTIALLY_PROCESSED,
	/** Processing failed; the article was preserved for later repair. */
	FAILED,
	/** Article was rejected because it was low quality or unusable. */
	UNSUPPORTED
}