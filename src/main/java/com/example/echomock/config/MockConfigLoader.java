package com.example.echomock.config;

import com.example.echomock.model.MockDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads mock definitions from the external YAML file at {@code mock.config-path}
 * (default {@code ./config/mocks.yml}, override with {@code MOCK_CONFIG_PATH}). The
 * file is not bundled in the WAR, so it is supplied per environment and can be
 * edited without rebuilding. If the file is missing, startup fails with a clear,
 * actionable error.
 *
 * <p>The loaded list is held in an {@link AtomicReference} so it can be hot-reloaded
 * at runtime via the admin endpoint.</p>
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

    /** (Re)reads the external config file and atomically swaps in the new definitions. */
    public synchronized List<MockDefinition> reload() {
        File external = new File(externalConfigPath);
        if (!external.isFile()) {
            String msg = "Mock config file not found at '" + external.getAbsolutePath()
                    + "'. Provide it via the MOCK_CONFIG_PATH env var (or mount it there); "
                    + "it is intentionally not bundled in the WAR.";
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        try (InputStream in = Files.newInputStream(external.toPath())) {
            log.info("Loading mock config from external file: {}", external.getAbsolutePath());
            MockConfigFile parsed = yaml.readValue(in, MockConfigFile.class);
            List<MockDefinition> mocks = parsed.getMocks() == null
                    ? Collections.emptyList() : parsed.getMocks();
            definitions.set(mocks);
            log.info("Loaded {} mock definition(s)", mocks.size());
            return mocks;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse mock config '{}': {}", externalConfigPath, e.getMessage(), e);
            throw new IllegalStateException("Unable to load mock config from " + externalConfigPath, e);
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
