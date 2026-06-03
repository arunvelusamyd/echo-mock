package com.example.echomock.engine;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evaluates the optional {@code when} conditions on a response. Each entry maps a
 * {@link RequestContext} reference (e.g. {@code body.type}) to an expected value.
 * All entries must pass for the condition block to match.
 *
 * <p>Supported value operators:</p>
 * <ul>
 *   <li>{@code exact} — plain string equality (default)</li>
 *   <li>{@code !=value} — not equal</li>
 *   <li>{@code >n} / {@code <n} / {@code >=n} / {@code <=n} — numeric comparison</li>
 *   <li>{@code regex:pattern} — full-match regular expression</li>
 *   <li>{@code exists} — reference resolves to a non-null value</li>
 *   <li>{@code missing} — reference is null/absent</li>
 * </ul>
 */
@Component
public class ConditionEvaluator {

    public boolean matches(Map<String, String> conditions, RequestContext ctx) {
        if (conditions == null || conditions.isEmpty()) {
            return true; // no conditions => default response
        }
        for (Map.Entry<String, String> e : conditions.entrySet()) {
            String actual = ctx.resolve(e.getKey());
            if (!matchesOne(actual, e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesOne(String actual, String expected) {
        if (expected == null) {
            return actual == null;
        }
        String exp = expected.trim();

        if (exp.equals("exists")) {
            return actual != null;
        }
        if (exp.equals("missing")) {
            return actual == null;
        }
        if (exp.startsWith("regex:")) {
            return actual != null && Pattern.matches(exp.substring(6), actual);
        }
        if (exp.startsWith("!=")) {
            return !exp.substring(2).equals(actual);
        }
        if (exp.startsWith(">=")) {
            return compare(actual, exp.substring(2)) >= 0;
        }
        if (exp.startsWith("<=")) {
            return compare(actual, exp.substring(2)) <= 0;
        }
        if (exp.startsWith(">")) {
            return compare(actual, exp.substring(1)) > 0;
        }
        if (exp.startsWith("<")) {
            return compare(actual, exp.substring(1)) < 0;
        }
        return exp.equals(actual);
    }

    /** Numeric comparison; returns Integer.MIN_VALUE-ish sentinel handling via 0 on parse failure. */
    private int compare(String actual, String expected) {
        try {
            double a = Double.parseDouble(actual.trim());
            double b = Double.parseDouble(expected.trim());
            return Double.compare(a, b);
        } catch (Exception e) {
            // Non-numeric — fall back to string comparison so it simply won't match ranges.
            return actual == null ? -1 : actual.compareTo(expected);
        }
    }
}
