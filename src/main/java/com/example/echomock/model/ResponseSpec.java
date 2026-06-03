package com.example.echomock.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One possible response for a mock definition. Responses are evaluated in order;
 * the first whose {@code when} conditions all match is returned. A response with
 * no {@code when} block acts as the default / fallback.
 *
 * <p>The {@code status}, {@code headers}, and {@code body} all support
 * {@code ${...}} template placeholders that are resolved against the incoming
 * request (see TemplateResolver), so an id received in the request body or a
 * header can be echoed straight back.</p>
 */
public class ResponseSpec {

    /**
     * Optional match conditions. Each entry is a template reference (e.g.
     * {@code body.type}, {@code header.X-Channel}) mapped to an expected value.
     * The value supports operators: plain equals, {@code !=value}, {@code >n},
     * {@code <n}, {@code regex:pattern}, {@code exists}, {@code missing}.
     */
    private Map<String, String> when;

    /** HTTP status to return. Supports templates, e.g. "${query.status:-200}". */
    private String status = "200";

    /** Response headers. Keys and values support templates. */
    private Map<String, String> headers = new LinkedHashMap<>();

    /** Response body. May be a String (raw template) or a nested map/list (rendered as JSON). */
    private Object body;

    /** Optional artificial latency in milliseconds, to simulate slow downstreams. */
    private long delayMs = 0;

    public Map<String, String> getWhen() {
        return when;
    }

    public void setWhen(Map<String, String> when) {
        this.when = when;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
