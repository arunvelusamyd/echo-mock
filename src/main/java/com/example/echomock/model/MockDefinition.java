package com.example.echomock.model;

import java.util.List;

/**
 * A single mock endpoint definition loaded from the config file.
 * Each definition matches an incoming request (method + path) and then
 * picks the first {@link ResponseSpec} whose conditions match.
 */
public class MockDefinition {

    private String name;
    private RequestSpec request = new RequestSpec();
    private List<ResponseSpec> responses;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RequestSpec getRequest() {
        return request;
    }

    public void setRequest(RequestSpec request) {
        this.request = request;
    }

    public List<ResponseSpec> getResponses() {
        return responses;
    }

    public void setResponses(List<ResponseSpec> responses) {
        this.responses = responses;
    }
}
