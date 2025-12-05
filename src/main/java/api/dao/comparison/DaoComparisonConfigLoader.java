package api.dao.comparison;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class DaoComparisonConfigLoader {

    private final Map<String, DaoComparisonRule> rules = new HashMap<>();

    public DaoComparisonConfigLoader(String configFile) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                throw new IllegalArgumentException("Config file not found: " + configFile);
            }
            Properties props = new Properties();
            props.load(input);
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                String[] target = value.split(":");
                if (target.length != 2) {
                    continue;
                }

                String daoClassName = target[0].trim();
                List<String> fields = Arrays.asList(target[1].split(","));

                rules.put(key.trim(), new DaoComparisonRule(daoClassName, fields));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DAO comparison config", e);
        }
    }

    public DaoComparisonRule getRuleFor(Class<?> apiResponseClass) {
        return rules.get(apiResponseClass.getSimpleName());
    }

    public static class DaoComparisonRule {
        private final String daoClassSimpleName;
        private final Map<String, String> fieldMappings;

        public DaoComparisonRule(String daoClassSimpleName, List<String> fieldPairs) {
            this.daoClassSimpleName = daoClassSimpleName;
            this.fieldMappings = new HashMap<>();

            for (String pair : fieldPairs) {
                String[] parts = pair.split("=");
                if (parts.length == 2) {
                    fieldMappings.put(parts[0].trim(), parts[1].trim());
                } else {
                    String name = pair.trim();
                    fieldMappings.put(name, name);
                }
            }
        }

        public String getDaoClassSimpleName() {
            return daoClassSimpleName;
        }

        public Map<String, String> getFieldMappings() {
            return fieldMappings;
        }
    }
}