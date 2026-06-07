package com.vanlo.newsfetch.config;

import com.vanlo.newsfetch.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class LocalProfileConfigurationTest {

    @Autowired
    private SourceConfigRegistry sourceConfigRegistry;

    @Test
    void localProfileLoadsVerifiedRssSources() {
        assertThat(sourceConfigRegistry.allSources())
                .extracting("id")
                .containsExactly(
                        "hacker-news-rss",
                        "techcrunch-rss",
                        "the-verge-rss",
                        "ars-technica-rss",
                        "bbc-business-rss"
                );
        assertThat(sourceConfigRegistry.allSources())
                .allSatisfy(source -> {
                    assertThat(source.type()).isEqualTo(SourceType.RSS);
                    assertThat(source.enabled()).isTrue();
                    assertThat(source.language()).isEqualTo("en");
                    assertThat(source.cacheTtlSeconds()).isEqualTo(600);
                });
    }
}
