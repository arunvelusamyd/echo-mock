package com.example.echomock.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private RequestContext ctx(String body) {
        return new RequestContext("POST", Map.of("x-channel", "web"), Map.of("q", "5"), Map.of(), body);
    }

    @Test
    void nullOrEmptyConditionsAlwaysMatch() {
        assertThat(evaluator.matches(null, ctx("{}"))).isTrue();
        assertThat(evaluator.matches(Map.of(), ctx("{}"))).isTrue();
    }

    @Test
    void exactMatch() {
        assertThat(evaluator.matches(Map.of("body.type", "PAYMENT"), ctx("{\"type\":\"PAYMENT\"}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.type", "PAYMENT"), ctx("{\"type\":\"REFUND\"}"))).isFalse();
    }

    @Test
    void notEqualOperator() {
        assertThat(evaluator.matches(Map.of("body.currency", "!=SGD"), ctx("{\"currency\":\"USD\"}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.currency", "!=SGD"), ctx("{\"currency\":\"SGD\"}"))).isFalse();
    }

    @Test
    void numericComparisons() {
        assertThat(evaluator.matches(Map.of("body.amount", ">100"), ctx("{\"amount\":150}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.amount", ">100"), ctx("{\"amount\":50}"))).isFalse();
        assertThat(evaluator.matches(Map.of("body.amount", "<100"), ctx("{\"amount\":50}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.amount", ">=100"), ctx("{\"amount\":100}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.amount", "<=100"), ctx("{\"amount\":100}"))).isTrue();
    }

    @Test
    void numericOperatorFallsBackToLexicographicWhenValueNonNumeric() {
        // When the value isn't numeric, comparison falls back to String.compareTo:
        // "abc" vs "100" -> 'a'(97) > '1'(49), so ">100" matches.
        assertThat(evaluator.matches(Map.of("body.amount", ">100"), ctx("{\"amount\":\"abc\"}"))).isTrue();
    }

    @Test
    void regexOperator() {
        assertThat(evaluator.matches(Map.of("body.ref", "regex:TXN-\\d+"), ctx("{\"ref\":\"TXN-123\"}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.ref", "regex:TXN-\\d+"), ctx("{\"ref\":\"X\"}"))).isFalse();
    }

    @Test
    void existsAndMissing() {
        assertThat(evaluator.matches(Map.of("body.id", "exists"), ctx("{\"id\":\"1\"}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.id", "exists"), ctx("{}"))).isFalse();
        assertThat(evaluator.matches(Map.of("body.id", "missing"), ctx("{}"))).isTrue();
        assertThat(evaluator.matches(Map.of("body.id", "missing"), ctx("{\"id\":\"1\"}"))).isFalse();
    }

    @Test
    void matchesAgainstHeaderAndQueryReferences() {
        assertThat(evaluator.matches(Map.of("header.X-Channel", "web"), ctx("{}"))).isTrue();
        assertThat(evaluator.matches(Map.of("query.q", ">3"), ctx("{}"))).isTrue();
    }

    @Test
    void allConditionsMustMatch() {
        Map<String, String> both = Map.of("body.type", "PAYMENT", "body.amount", ">100");
        assertThat(evaluator.matches(both, ctx("{\"type\":\"PAYMENT\",\"amount\":150}"))).isTrue();
        assertThat(evaluator.matches(both, ctx("{\"type\":\"PAYMENT\",\"amount\":1}"))).isFalse();
    }
}
