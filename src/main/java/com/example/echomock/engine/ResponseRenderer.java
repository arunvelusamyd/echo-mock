package com.example.echomock.engine;

import com.example.echomock.model.ResponseSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Turns a matched {@link ResponseSpec} into a concrete {@link ResponseEntity},
 * resolving {@code ${...}} templates in the status, headers, and body against the
 * incoming request.
 */
@Component
public class ResponseRenderer {

    private static final Logger log = LoggerFactory.getLogger(ResponseRenderer.class);

    private final TemplateResolver templates;
    private final ObjectMapper json = new ObjectMapper();

    public ResponseRenderer(TemplateResolver templates) {
        this.templates = templates;
    }

    public ResponseEntity<String> render(ResponseSpec spec, RequestContext ctx) {
        applyDelay(spec.getDelayMs());

        int status = resolveStatus(spec.getStatus(), ctx);

        HttpHeaders headers = new HttpHeaders();
        boolean contentTypeSet = false;
        if (spec.getHeaders() != null) {
            for (Map.Entry<String, String> h : spec.getHeaders().entrySet()) {
                String value = templates.render(h.getValue(), ctx);
                headers.add(h.getKey(), value);
                if (h.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
                    contentTypeSet = true;
                }
            }
        }

        String body = renderBody(spec.getBody(), ctx);

        if (!contentTypeSet) {
            headers.setContentType(looksLikeJson(body) ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN);
        }

        return ResponseEntity.status(status).headers(headers).body(body);
    }

    private int resolveStatus(String statusTemplate, RequestContext ctx) {
        String rendered = templates.render(statusTemplate, ctx);
        try {
            return Integer.parseInt(rendered.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid status '{}', defaulting to 200", rendered);
            return 200;
        }
    }

    /**
     * If the body is a String it is treated as a raw template. If it is a structured
     * map/list (from YAML), it is serialized to JSON and then templated, so
     * placeholders inside nested fields are resolved too.
     */
    private String renderBody(Object body, RequestContext ctx) {
        if (body == null) {
            return "";
        }
        String raw;
        if (body instanceof String s) {
            raw = s;
        } else {
            try {
                raw = json.writeValueAsString(body);
            } catch (Exception e) {
                log.error("Failed to serialize body to JSON: {}", e.getMessage());
                raw = String.valueOf(body);
            }
        }
        return templates.render(raw, ctx);
    }

    private boolean looksLikeJson(String body) {
        if (body == null) {
            return false;
        }
        String t = body.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private void applyDelay(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
