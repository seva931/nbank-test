package models.comparison;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ModelComparisonConfigLoader {

    private final Map<String, ComparisonRule> rules = new HashMap<>();

    public ModelComparisonConfigLoader(String configFileOnClasspath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(configFileOnClasspath)) {
            if (in == null) {
                throw new IllegalArgumentException("Config file not found on classpath: " + configFileOnClasspath);
            }
            Properties props = new Properties();
            props.load(in);

            for (String key : props.stringPropertyNames()) {
                String raw = props.getProperty(key);
                String[] parts = raw.split(":", 2);
                if (parts.length != 2) continue;

                String responseSimple = parts[0].trim();
                String fieldsRaw = parts[1].trim();

                List<String> pairs = fieldsRaw.isEmpty()
                        ? Collections.emptyList()
                        : Arrays.asList(fieldsRaw.split(","));

                rules.put(key.trim(), new ComparisonRule(responseSimple, pairs));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DTO comparison config", e);
        }
    }

    public ComparisonRule getRuleFor(Class<?> requestClass) {
        return rules.get(requestClass.getSimpleName());
    }

    public static final class ComparisonRule {
        private final String responseClassSimpleName;
        private final Map<String, String> fieldMappings;

        public ComparisonRule(String responseClassSimpleName, List<String> fieldPairs) {
            this.responseClassSimpleName = responseClassSimpleName;
            this.fieldMappings = new HashMap<>();
            for (String pair : fieldPairs) {
                String[] p = pair.split("=");
                if (p.length == 2) {
                    fieldMappings.put(p[0].trim(), p[1].trim());
                } else {
                    String name = pair.trim();
                    if (!name.isEmpty()) fieldMappings.put(name, name);
                }
            }
        }

        public String getResponseClassSimpleName() {
            return responseClassSimpleName;
        }

        public Map<String, String> getFieldMappings() {
            return fieldMappings;
        }
    }
}
