package com.example.echomock.config;

import com.example.echomock.model.MockDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockConfigLoaderTest {

    private MockConfigLoader loaderWithPath(String path) {
        MockConfigLoader loader = new MockConfigLoader();
        ReflectionTestUtils.setField(loader, "externalConfigPath", path);
        return loader;
    }

    @Test
    void fallsBackToClasspathWhenExternalFileMissing() {
        MockConfigLoader loader = loaderWithPath("does/not/exist.yml");
        List<MockDefinition> defs = loader.reload();
        // The bundled classpath mocks.yml ships 3 definitions.
        assertThat(defs).hasSize(3);
        assertThat(loader.getDefinitions()).hasSize(3);
    }

    @Test
    void loadsExternalFileWhenPresent(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mocks.yml");
        Files.writeString(file, """
                mocks:
                  - name: ping
                    request:
                      method: GET
                      path: /ping
                    responses:
                      - status: "200"
                        body: "pong"
                """);
        MockConfigLoader loader = loaderWithPath(file.toString());

        List<MockDefinition> defs = loader.reload();

        assertThat(defs).hasSize(1);
        assertThat(defs.get(0).getName()).isEqualTo("ping");
        assertThat(defs.get(0).getRequest().getPath()).isEqualTo("/ping");
    }

    @Test
    void emptyMocksKeyYieldsEmptyList(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mocks.yml");
        Files.writeString(file, "mocks:\n");
        MockConfigLoader loader = loaderWithPath(file.toString());

        assertThat(loader.reload()).isEmpty();
    }

    @Test
    void invalidYamlThrowsIllegalState(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mocks.yml");
        Files.writeString(file, "mocks: [this is : not valid yaml : : :");
        MockConfigLoader loader = loaderWithPath(file.toString());

        assertThatThrownBy(loader::reload).isInstanceOf(IllegalStateException.class);
    }
}
