package com.example.echomock.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateResolverTest {

    private final TemplateResolver resolver = new TemplateResolver();

    private RequestContext ctx(String body) {
        return new RequestContext("POST", Map.of("x-tracking-id", "TRK-1"), Map.of("status", "200"),
                Map.of("id", "PATH-9"), body);
    }

    @Test
    void returnsNullUnchanged() {
        assertThat(resolver.render(null, ctx("{}"))).isNull();
    }

    @Test
    void returnsLiteralWithoutPlaceholders() {
        assertThat(resolver.render("plain text", ctx("{}"))).isEqualTo("plain text");
    }

    @Test
    void resolvesBodyHeaderQueryAndPathReferences() {
        assertThat(resolver.render("${body.txn}", ctx("{\"txn\":\"T1\"}"))).isEqualTo("T1");
        assertThat(resolver.render("${header.X-Tracking-Id}", ctx("{}"))).isEqualTo("TRK-1");
        assertThat(resolver.render("${query.status}", ctx("{}"))).isEqualTo("200");
        assertThat(resolver.render("${path.id}", ctx("{}"))).isEqualTo("PATH-9");
    }

    @Test
    void missingReferenceBecomesEmptyString() {
        assertThat(resolver.render("[${body.nope}]", ctx("{}"))).isEqualTo("[]");
    }

    @Test
    void usesDefaultWhenReferenceMissing() {
        assertThat(resolver.render("${body.nope:-fallback}", ctx("{}"))).isEqualTo("fallback");
    }

    @Test
    void prefersValueOverDefaultWhenPresent() {
        assertThat(resolver.render("${body.txn:-fallback}", ctx("{\"txn\":\"T1\"}"))).isEqualTo("T1");
    }

    @Test
    void resolvesNestedDefaultInsideOut() {
        // header present -> header wins
        assertThat(resolver.render("${header.X-Tracking-Id:-${body.trackingId}}", ctx("{}")))
                .isEqualTo("TRK-1");
        // header absent -> falls back to body
        RequestContext noHeader = new RequestContext("POST", Map.of(), Map.of(), Map.of(),
                "{\"trackingId\":\"FROM-BODY\"}");
        assertThat(resolver.render("${header.X-Tracking-Id:-${body.trackingId}}", noHeader))
                .isEqualTo("FROM-BODY");
    }

    @Test
    void resolvesMultiplePlaceholdersInOneString() {
        String out = resolver.render("{\"a\":\"${body.a}\",\"b\":\"${path.id}\"}", ctx("{\"a\":\"AA\"}"));
        assertThat(out).isEqualTo("{\"a\":\"AA\",\"b\":\"PATH-9\"}");
    }

    @Test
    void resolvesGeneratedUuidPlaceholder() {
        assertThat(resolver.render("${uuid}", ctx("{}"))).matches("[0-9a-f\\-]{36}");
    }
}
