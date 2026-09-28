@TestOn('vm')
library;

import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:cryptosignals/core/constants/api_constants.dart';
import 'package:cryptosignals/core/di/providers.dart';
import 'package:cryptosignals/data/datasources/token_local_data_source.dart';
import 'package:cryptosignals/presentation/providers/news_providers.dart';
import 'package:cryptosignals/presentation/screens/news/news_screen.dart';
import 'package:cryptosignals/presentation/widgets/news_article_card.dart';

/// LIVE end-to-end test against a running backend on localhost:8080.
/// Disabled unless NEWS_LIVE=true so normal `flutter test` stays offline.
class _LiveTokens extends TokenLocalDataSource {
  String? token;

  @override
  Future<String?> readAccessToken() async => token;
}

Future<String> _login(Dio dio) async {
  final email = 'e2e+${DateTime.now().microsecondsSinceEpoch}@example.com';
  await dio.post('/auth/signup',
      data: {'email': email, 'password': 'Abcdef12', 'fullName': 'Flutter E2E'});
  final login = await dio.post<Map<String, dynamic>>('/auth/login',
      data: {'email': email, 'password': 'Abcdef12'});
  return login.data!['accessToken'] as String;
}

void main() {
  // flutter_test installs HttpOverrides that force every HTTP response to 400.
  // Initialise the binding first, then clear the override so this live suite
  // can talk to the real backend.
  TestWidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = null;

  final runLive = Platform.environment['NEWS_LIVE'] == 'true';

  test('LIVE datasource->repository->controller against the running backend',
      () async {
    final tokens = _LiveTokens();
    final container = ProviderContainer(
      overrides: [tokenLocalDataSourceProvider.overrideWith((ref) => tokens)],
    );
    addTearDown(container.dispose);

    final dio = Dio(BaseOptions(baseUrl: ApiConstants.baseUrl));
    tokens.token = await _login(dio);
    expect(tokens.token, isNotEmpty);

    // Real NewsRemoteDataSource -> NewsRepositoryImpl -> NewsFeedController.
    final feed = await container.read(newsFeedControllerProvider.future);
    expect(feed.articles, isNotEmpty, reason: 'live backend returned no articles');
    for (var i = 0; i + 1 < feed.articles.length; i++) {
      expect(
        feed.articles[i].publishedAt.isBefore(feed.articles[i + 1].publishedAt),
        isFalse,
        reason: 'feed must be newest-first',
      );
    }

    final ctrl = container.read(newsFeedControllerProvider.notifier);
    await ctrl.loadMore(); // page 1
    var ids = container
        .read(newsFeedControllerProvider)
        .value!
        .articles
        .map((a) => a.id)
        .toList();
    expect(ids.toSet().length, ids.length, reason: 'duplicate ids rendered');

    // Refresh must reset the cursor; the next loadMore is page 1, not page 2.
    await ctrl.refresh();
    await ctrl.loadMore();
    ids = container
        .read(newsFeedControllerProvider)
        .value!
        .articles
        .map((a) => a.id)
        .toList();
    expect(ids.toSet().length, ids.length);

    // Live detail path.
    final detail =
        await container.read(newsDetailProvider(feed.articles.first.id).future);
    expect(detail.article.id, feed.articles.first.id);
  }, skip: !runLive);

  testWidgets('LIVE NewsScreen renders articles from the running backend',
      (tester) async {
    final tokens = _LiveTokens();
    final container = ProviderContainer(
      overrides: [tokenLocalDataSourceProvider.overrideWith((ref) => tokens)],
    );
    addTearDown(container.dispose);

    await tester.runAsync(() async {
      final dio = Dio(BaseOptions(baseUrl: ApiConstants.baseUrl));
      tokens.token = await _login(dio);
      // Warm the real provider against the live backend before first build.
      final feed = await container.read(newsFeedControllerProvider.future);
      expect(feed.articles, isNotEmpty);
    });

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(home: Scaffold(body: NewsScreen())),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.byType(NewsArticleCard), findsWidgets,
        reason: 'live articles must render on the News screen');
  }, skip: !runLive);
}
