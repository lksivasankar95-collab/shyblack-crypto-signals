package com.shyblack.cryptosignals.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Feed-configuration semantics used by the RSS provider's enablement. */
class NewsPropertiesTest {

    private static NewsProperties props(List<String> feeds) {
        return new NewsProperties(true, "0 */15 * * * *", 10, 20, 200, 48, "", feeds);
    }

    @Test
    void hasFeeds_isFalseWhenNull() {
        assertThat(props(null).hasFeeds()).isFalse();
        assertThat(props(null).activeFeeds()).isEmpty();
    }

    @Test
    void hasFeeds_isFalseWhenAllBlank() {
        NewsProperties p = props(List.of("", "   ", "\t"));
        assertThat(p.hasFeeds()).isFalse();
        assertThat(p.activeFeeds()).isEmpty();
    }

    @Test
    void activeFeeds_filtersBlanksAndReportsPresence() {
        NewsProperties p = props(List.of("https://a/rss", "", "https://b/feed", "  "));
        assertThat(p.hasFeeds()).isTrue();
        assertThat(p.activeFeeds()).containsExactly("https://a/rss", "https://b/feed");
    }
}
