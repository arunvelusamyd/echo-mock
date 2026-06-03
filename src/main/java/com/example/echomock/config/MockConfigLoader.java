package com.example.echomock.config;

import com.example.echomock.model.MockDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads mock definitions from a YAML file. Resolution order:
 * <ol>
 *   <li>External file at {@code mock.config-path} (so it can be edited without rebuilding)</li>
 *   <li>Classpath fallback {@code mocks.yml}</li>
 * </ol>
 * The loaded list is held in an {@link AtomicReference} so it can be hot-reloaded
 * at runtime via the admin endpoint.
 */
@Component
public class MockConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(MockConfigLoader.class);

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Value("${mock.config-path:config/mocks.yml}")
    private String externalConfigPath;

    private final AtomicReference<List<MockDefinition>> definitions =
            new AtomicReference<>(Collections.emptyList());

    @PostConstruct
    public void init() {
        reload();
    }

    public List<MockDefinition> getDefinitions() {
        return definitions.get();
    }

    /** (Re)reads the config file and atomically swaps in the new definitions. */
    public synchronized List<MockDefinition> reload() {
        try {
            MockConfigFile parsed;
            File external = new File(externalConfigPath);
            if (external.isFile()) {
                log.info("Loading mock config from external file: {}", external.getAbsolutePath());
                try (InputStream in = Files.newInputStream(external.toPath())) {
                    parsed = yaml.readValue(in, MockConfigFile.class);
                }
            } else {
                log.info("External config '{}' not found, falling back to classpath mocks.yml",
                        external.getAbsolutePath());
                try (InputStream in = new ClassPathResource("mocks.yml").getInputStream()) {
                    parsed = yaml.readValue(in, MockConfigFile.class);
                }
            }
            List<MockDefinition> mocks = parsed.getMocks() == null
                    ? Collections.emptyList() : parsed.getMocks();
            definitions.set(mocks);
            log.info("Loaded {} mock definition(s)", mocks.size());
            return mocks;
        } catch (Exception e) {
            log.error("Failed to load mock config: {}", e.getMessage(), e);
            throw new IllegalStateException("Unable to load mock config", e);
        }
    }

    /** Root wrapper matching the top-level {@code mocks:} key in the YAML file. */
    public static class MockConfigFile {
        private List<MockDefinition> mocks;

        public List<MockDefinition> getMocks() {
            return mocks;
        }

        public void setMocks(List<MockDefinition> mocks) {
            this.mocks = mocks;
        }
    }
}
