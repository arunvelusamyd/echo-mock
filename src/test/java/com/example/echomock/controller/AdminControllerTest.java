package com.example.echomock.controller;

import com.example.echomock.config.MockConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminControllerTest {

    private AdminController controller;

    @BeforeEach
    void setUp() {
        MockConfigLoader loader = new MockConfigLoader();
        // No external file -> classpath fallback (3 mocks).
        ReflectionTestUtils.setField(loader, "externalConfigPath", "none.yml");
        loader.reload();
        controller = new AdminController(loader);
    }

    @Test
    void healthReportsUpAndMockCount() {
        Map<String, Object> health = controller.health();
        assertThat(health).containsEntry("status", "UP").containsEntry("mocks", 3);
    }

    @Test
    void reloadReturnsCountAndNames() {
        Map<String, Object> result = controller.reload();
        assertThat(result).containsEntry("status", "RELOADED").containsEntry("mocks", 3);
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) result.get("names");
        assertThat(names).contains("create-transaction", "create-payment", "get-transaction-status");
    }

    @Test
    void listExposesDefinitionMetadata() {
        List<Map<String, Object>> list = controller.list();
        assertThat(list).hasSize(3);
        Map<String, Object> first = list.get(0);
        assertThat(first).containsKeys("name", "method", "path", "responses");
        assertThat(first.get("name")).isEqualTo("create-transaction");
    }
}
