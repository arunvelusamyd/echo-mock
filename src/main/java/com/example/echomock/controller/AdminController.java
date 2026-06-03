package com.example.echomock.controller;

import com.example.echomock.config.MockConfigLoader;
import com.example.echomock.model.MockDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin endpoints for operating the mock at runtime. Mapped under {@code /__admin},
 * which is more specific than the catch-all {@code /**} so it takes precedence.
 */
@RestController
@RequestMapping("/__admin")
public class AdminController {

    private final MockConfigLoader configLoader;

    public AdminController(MockConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    /** Liveness check. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "mocks", configLoader.getDefinitions().size());
    }

    /** Re-reads the config file so edits take effect without a restart. */
    @PostMapping("/reload")
    public Map<String, Object> reload() {
        List<MockDefinition> defs = configLoader.reload();
        return Map.of("status", "RELOADED", "mocks", defs.size(), "names", names(defs));
    }

    /** Lists the currently loaded mock definitions. */
    @GetMapping("/mocks")
    public List<Map<String, Object>> list() {
        return configLoader.getDefinitions().stream().map(def -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", def.getName());
            m.put("method", def.getRequest().getMethod());
            m.put("path", def.getRequest().getPath());
            m.put("responses", def.getResponses() == null ? 0 : def.getResponses().size());
            return m;
        }).collect(Collectors.toList());
    }

    private List<String> names(List<MockDefinition> defs) {
        return defs.stream().map(MockDefinition::getName).collect(Collectors.toList());
    }
}
