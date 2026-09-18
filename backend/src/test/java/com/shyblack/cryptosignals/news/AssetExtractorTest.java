package com.shyblack.cryptosignals.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.shyblack.cryptosignals.entity.enums.NewsAssetRelationshipType;
import com.shyblack.cryptosignals.market.MarketBook;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetExtractorTest {

	private final AssetExtractor extractor = new AssetExtractor(new MarketBook());

	@Test
	void extractsPrimaryAssetFromTitleAndMentionFromSummary() {
		List<AssetExtractor.ExtractedAsset> assets = extractor.extract(
				"Bitcoin surges as ETF inflows hit record high",
				"Solana and Ethereum also moved higher in tandem.");

		assertThat(assets).isNotEmpty();
		AssetExtractor.ExtractedAsset btc = asset(assets, "BTC");
		assertThat(btc).isNotNull();
		assertThat(btc.name()).isEqualTo("Bitcoin");
		assertThat(btc.type()).isEqualTo(NewsAssetRelationshipType.PRIMARY);
		assertThat(btc.relevanceScore()).isEqualTo(1.0);

		AssetExtractor.ExtractedAsset eth = asset(assets, "ETH");
		assertThat(eth).isNotNull();
		assertThat(eth.type()).isEqualTo(NewsAssetRelationshipType.MENTIONED);
	}

	@Test
	void symbolTokenMatches() {
		List<AssetExtractor.ExtractedAsset> assets = extractor.extract(
				"SOL breaks out above $200", "");
		assertThat(asset(assets, "SOL")).isNotNull();
	}

	@Test
	void ignoresPartialMatchesAndNonAssets() {
		List<AssetExtractor.ExtractedAsset> assets = extractor.extract(
				"The sandbox raised a new mining fund", "");
		assertThat(asset(assets, "SAND")).isNull();
		assertThat(asset(assets, "DOGE")).isNull();
	}

	@Test
	void usesLiveDirectoryWhenSeeded() {
		MarketBook book = new MarketBook();
		book.spotSymbols().replace(java.util.Map.of("MYCOINUSDT", "MyCoin",
				"BTCUSDT", "Bitcoin"));
		AssetExtractor local = new AssetExtractor(book);

		List<AssetExtractor.ExtractedAsset> assets = local.extract(
				"MYCOIN launches mainnet", "");
		assertThat(asset(assets, "MYCOIN")).isNotNull();
	}

	@Test
	void baseSymbolStripsUsdtQuote() {
		assertThat(AssetExtractor.baseSymbol("BTCUSDT")).isEqualTo("BTC");
		assertThat(AssetExtractor.baseSymbol("BTC")).isNull();
		assertThat(AssetExtractor.baseSymbol("BTCUSD")).isNull();
	}

	private static AssetExtractor.ExtractedAsset asset(List<AssetExtractor.ExtractedAsset> assets, String symbol) {
		return assets.stream().filter(a -> a.symbol().equals(symbol)).findFirst().orElse(null);
	}
}