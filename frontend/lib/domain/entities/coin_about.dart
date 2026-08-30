/// Placeholder blurbs until a real asset-metadata API exists. Do not invent
/// market-cap or supply figures here.
abstract final class CoinAboutCopy {
  static const Map<String, String> _byBase = {
    'BTC':
        'Bitcoin is the original cryptocurrency: a decentralized digital asset secured by proof-of-work. It is widely used as a store of value and the quote pair for much of the crypto market.',
    'ETH':
        'Ethereum is a smart-contract platform. Ether (ETH) pays for computation and secures the network. Most DeFi and NFT activity still settles here or on L2s that inherit its security.',
    'BNB':
        'BNB is the native asset of the BNB Chain ecosystem. It is used for trading-fee discounts, gas on BNB Smart Chain, and various Binance-related products.',
    'SOL':
        'Solana is a high-throughput L1 known for low fees and fast finality. SOL is used for fees, staking, and as the base asset across Solana DeFi.',
    'XRP':
        'XRP is the native asset of the XRP Ledger, designed for fast, low-cost transfers. It is commonly used as a bridge currency between fiat and crypto rails.',
    'ADA':
        'Cardano is a proof-of-stake L1. ADA is used for fees, staking, and governance. The chain emphasizes formal methods and a staged roadmap.',
    'DOGE':
        'Dogecoin started as a meme and remains a widely traded, proof-of-work coin with an active community and relatively simple payment-focused design.',
    'TON':
        'Toncoin is the native asset of The Open Network, a layer-1 originally tied to the Telegram ecosystem, used for fees, staking, and on-chain apps.',
    'AVAX':
        'Avalanche is an L1 with subnet architecture. AVAX pays fees and secures the primary network. It is used across DeFi and custom app chains.',
    'LINK':
        'Chainlink provides decentralized oracle networks that bring off-chain data on-chain. LINK is used to pay node operators and participate in staking.',
  };

  static String forSymbol(String symbol, String baseSymbol) {
    return _byBase[baseSymbol.toUpperCase()] ??
        _byBase[symbol.toUpperCase()] ??
        'No description available';
  }
}
