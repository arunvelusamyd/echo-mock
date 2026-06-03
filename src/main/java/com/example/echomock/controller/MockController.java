package com.example.echomock.controller;

import com.example.echomock.config.MockConfigLoader;
import com.example.echomock.engine.ConditionEvaluator;
import com.example.echomock.engine.PathMatcher;
import com.example.echomock.engine.RequestContext;
import com.example.echomock.engine.ResponseRenderer;
import com.example.echomock.model.MockDefinition;
import com.example.echomock.model.ResponseSpec;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catch-all controller. Every incoming request is matched against the loaded mock
 * definitions; the first definition matching method + path wins, then the first of
 * its responses whose {@code when} conditions match is rendered and returned.
 */
@RestController
public class MockController {

    private static final Logger log = LoggerFactory.getLogger(MockController.class);

    private final MockConfigLoader configLoader;
    private final ConditionEvaluator conditions;
    private final ResponseRenderer renderer;

    public MockController(MockConfigLoader configLoader,
                          ConditionEvaluator conditions,
                          ResponseRenderer renderer) {
        this.configLoader = configLoader;
        this.conditions = conditions;
        this.renderer = renderer;
    }

    @RequestMapping("/**")
    public ResponseEntity<String> handle(HttpServletRequest request,
                                         @RequestBody(required = false) String body) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        Map<String, String> headers = extractHeaders(request);
        Map<String, String> query = extractQuery(request);

        for (MockDefinition def : configLoader.getDefinitions()) {
            if (!methodMatches(def, method)) {
                continue;
            }
            Map<String, String> pathVars = PathMatcher.match(def.getRequest().getPath(), path);
            if (pathVars == null) {
                continue;
            }

            RequestContext ctx = new RequestContext(method, headers, query, pathVars, body);
            ResponseSpec chosen = pickResponse(def, ctx);
            if (chosen == null) {
                log.warn("Mock '{}' matched {} {} but no response condition matched", def.getName(), method, path);
                return notMatched(method, path, "matched endpoint but no response condition matched");
            }
            log.info("Matched mock '{}' for {} {}", def.getName(), method, path);
            return renderer.render(chosen, ctx);
        }

        log.info("No mock matched {} {}", method, path);
        return notMatched(method, path, "no mock definition matched this request");
    }

    private ResponseSpec pickResponse(MockDefinition def, RequestContext ctx) {
        List<ResponseSpec> responses = def.getResponses();
        if (responses == null) {
            return null;
        }
        for (ResponseSpec r : responses) {
            if (conditions.matches(r.getWhen(), ctx)) {
                return r;
            }
        }
        return null;
    }

    private boolean methodMatches(MockDefinition def, String method) {
        String configured = def.getRequest().getMethod();
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("ANY")) {
            return true;
        }
        return configured.equalsIgnoreCase(method);
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }

    private Map<String, String> extractQuery(HttpServletRequest request) {
        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                query.put(k, v[0]);
            }
        });
        return query;
    }

    private ResponseEntity<String> notMatched(String method, String path, String reason) {
        String payload = "{\"error\":\"NO_MOCK_MATCHED\",\"method\":\"" + method
                + "\",\"path\":\"" + path + "\",\"reason\":\"" + reason + "\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return ResponseEntity.status(404).headers(headers).body(payload);
    }
}
