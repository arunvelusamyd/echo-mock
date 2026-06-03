package com.example.echomock.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathMatcherTest {

    @Test
    void matchesExactLiteralPath() {
        Map<String, String> vars = PathMatcher.match("/api/transactions", "/api/transactions");
        assertThat(vars).isNotNull().isEmpty();
    }

    @Test
    void extractsSinglePathVariable() {
        Map<String, String> vars = PathMatcher.match("/api/transactions/{id}/status", "/api/transactions/TXN-1/status");
        assertThat(vars).containsEntry("id", "TXN-1");
    }

    @Test
    void extractsMultiplePathVariables() {
        Map<String, String> vars = PathMatcher.match("/accounts/{acc}/tx/{id}", "/accounts/A1/tx/T9");
        assertThat(vars).containsEntry("acc", "A1").containsEntry("id", "T9");
    }

    @Test
    void ignoresLeadingAndTrailingSlashes() {
        Map<String, String> vars = PathMatcher.match("api/x/", "/api/x");
        assertThat(vars).isNotNull().isEmpty();
    }

    @Test
    void returnsNullWhenSegmentCountDiffers() {
        assertThat(PathMatcher.match("/api/x", "/api/x/y")).isNull();
    }

    @Test
    void returnsNullWhenLiteralSegmentDiffers() {
        assertThat(PathMatcher.match("/api/x", "/api/y")).isNull();
    }

    @Test
    void matchesRootPath() {
        assertThat(PathMatcher.match("/", "/")).isNotNull().isEmpty();
    }

    @Test
    void handlesNullPatternOrActual() {
        assertThat(PathMatcher.match(null, "/")).isNotNull().isEmpty();
        assertThat(PathMatcher.match("/a", null)).isNull();
    }
}
