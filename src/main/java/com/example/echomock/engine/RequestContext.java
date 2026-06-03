package com.example.echomock.engine;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot of one incoming request, exposing a single {@link #resolve(String)}
 * method that template placeholders and {@code when} conditions both use.
 *
 * <p>Supported reference prefixes:</p>
 * <ul>
 *   <li>{@code body.<jsonPath>} — value from the JSON body. Dotted paths work for
 *       nested fields ({@code body.payer.accountId}); also accepts raw JsonPath
 *       ({@code body.$.items[0].id}).</li>
 *   <li>{@code header.<name>} — request header (case-insensitive)</li>
 *   <li>{@code query.<name>} — query string parameter</li>
 *   <li>{@code path.<var>} — path template variable</li>
 *   <li>{@code method} — HTTP method</li>
 *   <li>{@code uuid} — a random UUID generated per reference</li>
 *   <li>{@code timestamp} — epoch millis; {@code now} — ISO-8601 instant</li>
 * </ul>
 */
public class RequestContext {

    private static final Logger log = LoggerFactory.getLogger(RequestContext.class);

    private static final Configuration JSONPATH_CONFIG = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
            .build();

    private final String method;
    private final Map<String, String> headers;   // lower-cased keys
    private final Map<String, String> query;
    private final Map<String, String> pathVars;
    private final String rawBody;
    private final DocumentContext bodyJson;       // nullable when body is not JSON

    public RequestContext(String method,
                          Map<String, String> headers,
                          Map<String, String> query,
                          Map<String, String> pathVars,
                          String rawBody) {
        this.method = method == null ? "" : method;
        this.headers = headers == null ? Collections.emptyMap() : headers;
        this.query = query == null ? Collections.emptyMap() : query;
        this.pathVars = pathVars == null ? Collections.emptyMap() : pathVars;
        this.rawBody = rawBody == null ? "" : rawBody;
        this.bodyJson = parseJson(this.rawBody);
    }

    private static DocumentContext parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JsonPath.using(JSONPATH_CONFIG).parse(body);
        } catch (Exception e) {
            log.debug("Request body is not valid JSON; body.* references will be null");
            return null;
        }
    }

    /**
     * Resolves a single reference string to its value, or {@code null} if absent.
     */
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String ref = reference.trim();

        if (ref.equals("method")) {
            return method;
        }
        if (ref.equals("uuid")) {
            return UUID.randomUUID().toString();
        }
        if (ref.equals("timestamp")) {
            return String.valueOf(System.currentTimeMillis());
        }
        if (ref.equals("now")) {
            return Instant.now().toString();
        }

        int dot = ref.indexOf('.');
        if (dot < 0) {
            return null;
        }
        String scope = ref.substring(0, dot);
        String key = ref.substring(dot + 1);

        return switch (scope) {
            case "header" -> headers.get(key.toLowerCase());
            case "query" -> query.get(key);
            case "path" -> pathVars.get(key);
            case "body" -> resolveBody(key);
            default -> null;
        };
    }

    private String resolveBody(String key) {
        if (bodyJson == null) {
            return null;
        }
        String jsonPath = key.startsWith("$") ? key : "$." + key;
        try {
            Object value = bodyJson.read(jsonPath);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    public String getMethod() {
        return method;
    }

    public String getRawBody() {
        return rawBody;
    }
}
