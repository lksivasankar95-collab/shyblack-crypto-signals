package com.shyblack.cryptosignals.repository;

import com.shyblack.cryptosignals.entity.NewsAsset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsAssetRepository extends JpaRepository<NewsAsset, UUID> {

	List<NewsAsset> findByArticleId(UUID articleId);

	List<NewsAsset> findByArticleIdIn(Collection<UUID> articleIds);
}