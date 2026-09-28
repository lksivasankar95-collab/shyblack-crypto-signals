package com.shyblack.cryptosignals.service.news;

import java.util.UUID;

/**
 * Emitted once, inside the persistence transaction, when a genuinely new news
 * article has been stored. Consumed after commit so notification/WebSocket
 * work can never roll back the article.
 */
public record NewsCreatedEvent(UUID newsArticleId) {
}
