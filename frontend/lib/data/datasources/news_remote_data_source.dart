import '../../../core/constants/api_constants.dart';
import '../../../core/network/api_client.dart';
import '../models/news_model.dart';

class NewsQueryParams {
  const NewsQueryParams({
    this.category,
    this.sentiment,
    this.impact,
    this.source,
    this.query,
    this.from,
    this.to,
    this.page,
    this.size,
    this.sort,
    this.direction,
  });

  final String? category;
  final String? sentiment;
  final String? impact;
  final String? source;
  final String? query;
  final DateTime? from;
  final DateTime? to;
  final int? page;
  final int? size;
  final String? sort;
  final String? direction;

  Map<String, dynamic> toQueryParameters() {
    final params = <String, dynamic>{};
    if (category != null) params['category'] = category;
    if (sentiment != null) params['sentiment'] = sentiment;
    if (impact != null) params['impact'] = impact;
    if (source != null) params['source'] = source;
    if (query != null) params['q'] = query;
    if (from != null) params['from'] = from!.toUtc().toIso8601String();
    if (to != null) params['to'] = to!.toUtc().toIso8601String();
    if (page != null) params['page'] = page;
    if (size != null) params['size'] = size;
    if (sort != null) params['sort'] = sort;
    if (direction != null) params['direction'] = direction;
    return params;
  }
}

class NewsRemoteDataSource {
  NewsRemoteDataSource(this._apiClient);
  final ApiClient _apiClient;

  Future<NewsPageModel> getNews([NewsQueryParams params = const NewsQueryParams()]) {
    return _fetchPage(ApiConstants.news, params);
  }

  Future<NewsPageModel> searchNews([NewsQueryParams params = const NewsQueryParams()]) {
    return _fetchPage('${ApiConstants.news}/search', params);
  }

  Future<NewsPageModel> getAssetNews(
    String symbol, [
    NewsQueryParams params = const NewsQueryParams(),
  ]) {
    return _fetchPage(ApiConstants.newsAsset(symbol), params);
  }

  Future<NewsArticleDetailModel> getDetail(String id) async {
    final response = await _apiClient.dio
        .get<Map<String, dynamic>>(ApiConstants.newsDetail(id));
    return NewsArticleDetailModel.fromJson(response.data ?? const {});
  }

  Future<NewsMetaModel> getMeta() async {
    final response = await _apiClient.dio
        .get<Map<String, dynamic>>(ApiConstants.newsMeta);
    return NewsMetaModel.fromJson(response.data ?? const {});
  }

  Future<List<String>> getSources() async {
    final response = await _apiClient.dio
        .get<List<dynamic>>(ApiConstants.newsSources);
    return [
      for (final item in (response.data ?? const [])) item.toString(),
    ];
  }

  Future<NewsContextModel> getAssetContext(
    String symbol, {
    int? windowHours,
  }) async {
    final response = await _apiClient.dio.get<Map<String, dynamic>>(
      ApiConstants.newsAssetContext(symbol),
      queryParameters: {
        'windowHours': ?windowHours,
      },
    );
    return NewsContextModel.fromJson(response.data ?? const {});
  }

  Future<NewsPageModel> _fetchPage(
    String path,
    NewsQueryParams params,
  ) async {
    final response = await _apiClient.dio.get<Map<String, dynamic>>(
      path,
      queryParameters: params.toQueryParameters(),
    );
    return NewsPageModel.fromJson(response.data ?? const {});
  }
}