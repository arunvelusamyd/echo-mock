package com.example.echomock.model;

/**
 * Describes how to match an incoming request to a mock definition.
 * The {@code path} supports {@code {var}} segments, e.g. {@code /api/transactions/{id}},
 * which are then available to templates as {@code ${path.id}}.
 */
public class RequestSpec {

    /** HTTP method to match (GET, POST, ...). Use "ANY" or leave blank to match all methods. */
    private String method = "ANY";

    /** Path pattern to match, e.g. {@code /api/transactions} or {@code /accounts/{id}}. */
    private String path;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
