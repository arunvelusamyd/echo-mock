package com.example.echomock.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {

    private RequestContext ctx(String body) {
        return new RequestContext("POST",
                Map.of("x-tracking-id", "TRK-1"),
                Map.of("page", "2"),
                Map.of("id", "P-9"),
                body);
    }

    @Test
    void resolvesMethod() {
        assertThat(ctx("{}").resolve("method")).isEqualTo("POST");
        assertThat(ctx("{}").getMethod()).isEqualTo("POST");
    }

    @Test
    void resolvesGeneratedValues() {
        assertThat(ctx("{}").resolve("uuid")).matches("[0-9a-f\\-]{36}");
        assertThat(ctx("{}").resolve("timestamp")).matches("\\d+");
        assertThat(ctx("{}").resolve("now")).isNotBlank();
    }

    @Test
    void resolvesHeaderCaseInsensitively() {
        assertThat(ctx("{}").resolve("header.X-Tracking-Id")).isEqualTo("TRK-1");
        assertThat(ctx("{}").resolve("header.x-tracking-id")).isEqualTo("TRK-1");
    }

    @Test
    void resolvesQueryAndPath() {
        assertThat(ctx("{}").resolve("query.page")).isEqualTo("2");
        assertThat(ctx("{}").resolve("path.id")).isEqualTo("P-9");
    }

    @Test
    void resolvesNestedBodyField() {
        assertThat(ctx("{\"payer\":{\"id\":\"ACC-1\"}}").resolve("body.payer.id")).isEqualTo("ACC-1");
    }

    @Test
    void resolvesRawJsonPathInBody() {
        assertThat(ctx("{\"items\":[{\"id\":\"I0\"}]}").resolve("body.$.items[0].id")).isEqualTo("I0");
    }

    @Test
    void returnsNullForMissingBodyField() {
        assertThat(ctx("{\"a\":1}").resolve("body.b")).isNull();
    }

    @Test
    void nonJsonBodyYieldsNullBodyReferences() {
        assertThat(ctx("not-json").resolve("body.anything")).isNull();
    }

    @Test
    void blankAndNullBodyHandled() {
        RequestContext blank = new RequestContext("GET", Map.of(), Map.of(), Map.of(), "");
        assertThat(blank.resolve("body.x")).isNull();
        assertThat(blank.getRawBody()).isEmpty();
        RequestContext nulls = new RequestContext(null, null, null, null, null);
        assertThat(nulls.getMethod()).isEmpty();
        assertThat(nulls.resolve("body.x")).isNull();
    }

    @Test
    void unknownScopeAndMalformedReferenceReturnNull() {
        assertThat(ctx("{}").resolve("bogus.key")).isNull();
        assertThat(ctx("{}").resolve("noscope")).isNull();
        assertThat(ctx("{}").resolve(null)).isNull();
        assertThat(ctx("{}").resolve("  ")).isNull();
    }
}
