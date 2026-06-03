package com.example.echomock.engine;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code ${reference}} and {@code ${reference:-default}} placeholders in a
 * string using values pulled from a {@link RequestContext}.
 *
 * <p>Examples:</p>
 * <pre>
 *   ${body.transactionId}              -> value of transactionId in the request body
 *   ${header.X-Tracking-Id}            -> value of that header
 *   ${body.trackingId:-${header.X-Tracking-Id}}  -> body first, header fallback
 *   ${uuid}                            -> a generated UUID
 * </pre>
 * If a reference resolves to {@code null} and no default is given, the placeholder
 * is replaced with an empty string.
 */
@Component
public class TemplateResolver {

    // ${ ... } — matches an INNERMOST placeholder only (no braces inside), so nested
    // defaults like ${a:-${b}} are resolved inside-out across multiple passes.
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)\\}");

    public String render(String template, RequestContext ctx) {
        if (template == null || template.indexOf("${") < 0) {
            return template;
        }
        // Resolve repeatedly so nested defaults like ${a:-${b}} collapse fully.
        String current = template;
        for (int pass = 0; pass < 5 && current.indexOf("${") >= 0; pass++) {
            String next = renderOnce(current, ctx);
            if (next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    private String renderOnce(String template, RequestContext ctx) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String replacement = evaluate(expr, ctx);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String evaluate(String expr, RequestContext ctx) {
        String reference = expr;
        String fallback = null;

        int sep = expr.indexOf(":-");
        if (sep >= 0) {
            reference = expr.substring(0, sep);
            fallback = expr.substring(sep + 2);
        }

        String value = ctx.resolve(reference.trim());
        if (value != null) {
            return value;
        }
        return fallback != null ? fallback : "";
    }
}
