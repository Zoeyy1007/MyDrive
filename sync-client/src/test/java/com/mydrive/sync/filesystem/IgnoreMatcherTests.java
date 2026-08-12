package com.mydrive.sync.filesystem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IgnoreMatcherTests {
    private final IgnoreMatcher matcher = new IgnoreMatcher(List.of(
            ".DS_Store", "Thumbs.db", ".trash/**", "*.tmp", ".obsidian/workspace.json"));

    @Test
    void alwaysIgnoresInternalDatabaseAndDownloadTemps() {
        assertThat(matcher.isIgnored(".mydrive")).isTrue();
        assertThat(matcher.isIgnored(".mydrive/state.db")).isTrue();
        assertThat(matcher.isIgnored("folder/.photo.jpg.123.mydrive.tmp")).isTrue();
    }

    @Test
    void supportsNamesAndPortableGlobPatterns() {
        assertThat(matcher.isIgnored("Photos/.DS_Store")).isTrue();
        assertThat(matcher.isIgnored("Photos/Thumbs.db")).isTrue();
        assertThat(matcher.isIgnored(".trash/old/file.txt")).isTrue();
        assertThat(matcher.isIgnored("folder/cache.tmp")).isTrue();
        assertThat(matcher.isIgnored(".obsidian/workspace.json")).isTrue();
        assertThat(matcher.isIgnored("Photos/image.jpg")).isFalse();
    }

    @Test
    void normalizesWindowsSeparatorsBeforeMatching() {
        assertThat(matcher.isIgnored(".trash\\old\\file.txt")).isTrue();
    }
}
