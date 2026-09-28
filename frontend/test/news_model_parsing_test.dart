import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/constants/api_constants.dart';
import 'package:cryptosignals/data/datasources/news_remote_data_source.dart';
import 'package:cryptosignals/data/models/news_model.dart';
import 'package:cryptosignals/domain/entities/news.dart';

void main() {
  // Shape captured from the running backend (`/api/v1/news` -> PageResponse<NewsResponse>).
  final backendPayload = <String, dynamic>{
    'items': [
      {
        'id': '11111111-1111-1111-1111-111111111111',
        'sourceName': 'AMBCrypto',
        'sourceUrl': 'https://ambcrypto.com/pump-defends-50-day-ma',
        'title': 'PUMP defends 50-day MA',
        'summary': 'A short summary.',
        'imageUrl': null,
        'author': null,
        'publishedAt': '2026-09-27T08:00:22Z',
        'category': 'MARKET',
        'sentiment': 'POSITIVE',
        'sentimentScore': 0.5,
        'impactLevel': 'HIGH',
        'impactScore': 0.75,
        'newsScore': 3.2,
        'confidenceScore': 0.8,
        'assets': [
          {
            'symbol': 'BTC',
            'name': 'Bitcoin',
            'relevanceScore': 1.0,
            'relationshipType': 'PRIMARY',
          },
        ],
      },
    ],
    'page': 0,
    'size': 20,
    'totalElements': 200,
    'totalPages': 10,
    'hasNext': true,
  };

  test('parses the real backend news page shape', () {
    final page = NewsPageModel.fromJson(backendPayload).toEntity();

    expect(page.page, 0);
    expect(page.size, 20);
    expect(page.totalElements, 200);
    expect(page.hasNext, isTrue);
    expect(page.items, hasLength(1));

    final article = page.items.first;
    expect(article.id, '11111111-1111-1111-1111-111111111111');
    expect(article.sourceName, 'AMBCrypto');
    expect(article.title, 'PUMP defends 50-day MA');
    expect(article.category, NewsCategory.market);
    expect(article.sentiment, NewsSentiment.positive);
    expect(article.impactLevel, NewsImpact.high);
    expect(article.newsScore, 3.2);
    expect(article.assets.single.symbol, 'BTC');
    expect(
      article.assets.single.relationshipType,
      NewsAssetRelationshipType.primary,
    );
  });

  test('publishedAt preserves the UTC instant', () {
    final article = NewsPageModel.fromJson(backendPayload).toEntity().items.first;
    expect(article.publishedAt.isUtc, isTrue);
    expect(article.publishedAt, DateTime.utc(2026, 9, 27, 8, 0, 22));
    // Presenting in local time must not change the instant.
    expect(article.publishedAt.toLocal().toUtc(), article.publishedAt);
  });

  test('query params map filters with UTC timestamps and backend field names', () {
    final params = NewsQueryParams(
      query: 'btc',
      sort: 'publishedAt',
      direction: 'desc',
      page: 0,
      size: 20,
      from: DateTime.utc(2026, 1, 1),
    ).toQueryParameters();

    expect(params['q'], 'btc');
    expect(params['sort'], 'publishedAt');
    expect(params['direction'], 'desc');
    expect(params['page'], 0);
    expect(params['size'], 20);
    expect(params['from'], '2026-01-01T00:00:00.000Z');
  });

  test('news endpoint resolves to /api/v1/news', () {
    expect('${ApiConstants.baseUrl}${ApiConstants.news}',
        'http://localhost:8080/api/v1/news');
  });
}