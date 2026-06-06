package com.vanlo.newsfetch.config;

import com.vanlo.newsfetch.domain.SourceType;
import com.vanlo.newsfetch.infrastructure.SourceUrlValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SourceConfigRegistryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void loadsNoSourcesWhenConfigurationIsMissing() {
        contextRunner.run(context -> {
            SourceConfigRegistry registry = context.getBean(SourceConfigRegistry.class);

            assertThat(registry.allSources()).isEmpty();
            assertThat(registry.enabledSources()).isEmpty();
        });
    }

    @Test
    void loadsSourcesAndAppliesDefaults() {
        contextRunner
                .withPropertyValues(
                        "news-fetch.sources[0].id=tech-rss",
                        "news-fetch.sources[0].name=Tech RSS",
                        "news-fetch.sources[0].type=RSS",
                        "news-fetch.sources[0].url=https://example.com/rss.xml"
                )
                .run(context -> {
                    SourceConfigRegistry registry = context.getBean(SourceConfigRegistry.class);

                    assertThat(registry.allSources()).hasSize(1);
                    assertThat(registry.enabledSources()).hasSize(1);
                    assertThat(registry.findById("tech-rss")).hasValueSatisfying(source -> {
                        assertThat(source.type()).isEqualTo(SourceType.RSS);
                        assertThat(source.enabled()).isTrue();
                        assertThat(source.priority()).isEqualTo(100);
                        assertThat(source.method()).isEqualTo("GET");
                        assertThat(source.timeoutMs()).isEqualTo(5000);
                        assertThat(source.maxResponseBytes()).isEqualTo(1048576);
                        assertThat(source.retryCount()).isZero();
                        assertThat(source.headers()).isEmpty();
                        assertThat(source.params()).isEmpty();
                    });
                });
    }

    @Test
    void sortsSourcesByPriorityThenId() {
        contextRunner
                .withPropertyValues(
                        "news-fetch.sources[0].id=b-source",
                        "news-fetch.sources[0].name=B Source",
                        "news-fetch.sources[0].type=RSS",
                        "news-fetch.sources[0].url=https://example.com/b.xml",
                        "news-fetch.sources[0].priority=20",
                        "news-fetch.sources[1].id=a-source",
                        "news-fetch.sources[1].name=A Source",
                        "news-fetch.sources[1].type=RSS",
                        "news-fetch.sources[1].url=https://example.com/a.xml",
                        "news-fetch.sources[1].priority=10"
                )
                .run(context -> {
                    SourceConfigRegistry registry = context.getBean(SourceConfigRegistry.class);

                    assertThat(registry.allSources())
                            .extracting("id")
                            .containsExactly("a-source", "b-source");
                });
    }

    @Test
    void excludesDisabledSourcesFromEnabledList() {
        contextRunner
                .withPropertyValues(
                        "news-fetch.sources[0].id=disabled-source",
                        "news-fetch.sources[0].name=Disabled Source",
                        "news-fetch.sources[0].type=RSS",
                        "news-fetch.sources[0].url=https://example.com/rss.xml",
                        "news-fetch.sources[0].enabled=false"
                )
                .run(context -> {
                    SourceConfigRegistry registry = context.getBean(SourceConfigRegistry.class);

                    assertThat(registry.allSources()).hasSize(1);
                    assertThat(registry.enabledSources()).isEmpty();
                });
    }

    @Test
    void failsWhenRequiredSourceFieldsAreMissing() {
        contextRunner
                .withPropertyValues("news-fetch.sources[0].id=missing-required-fields")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenSourceUrlIsNotHttpOrHttps() {
        contextRunner
                .withPropertyValues(
                        "news-fetch.sources[0].id=local-file",
                        "news-fetch.sources[0].name=Local File",
                        "news-fetch.sources[0].type=RSS",
                        "news-fetch.sources[0].url=file:///tmp/rss.xml"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenSourceUrlTargetsPrivateNetwork() {
        contextRunner
                .withPropertyValues(
                        "news-fetch.sources[0].id=private-source",
                        "news-fetch.sources[0].name=Private Source",
                        "news-fetch.sources[0].type=RSS",
                        "news-fetch.sources[0].url=http://192.168.1.10/rss.xml"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenSourceIdsAreDuplicated() {
        contextRunner
                .withPropertyValues(
                        "news-fetch.sources[0].id=duplicate",
                        "news-fetch.sources[0].name=First Source",
                        "news-fetch.sources[0].type=RSS",
                        "news-fetch.sources[0].url=https://example.com/first.xml",
                        "news-fetch.sources[1].id=duplicate",
                        "news-fetch.sources[1].name=Second Source",
                        "news-fetch.sources[1].type=RSS",
                        "news-fetch.sources[1].url=https://example.com/second.xml"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(NewsFetchProperties.class)
    static class TestConfiguration {

        @Bean
        SourceConfigRegistry sourceConfigRegistry(NewsFetchProperties properties) {
            return new SourceConfigRegistry(
                    properties,
                    new SourceConfigValidator(new SourceUrlValidator())
            );
        }
    }
}
