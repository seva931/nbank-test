package api.dao.comparison;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;

public class DaoComparator {

    private final DaoComparisonConfigLoader configLoader =
            new DaoComparisonConfigLoader("dao-comparison.properties");

    public void compare(Object apiResponse, Object dao) {
        if (apiResponse == null) {
            throw new AssertionError("API model is null");
        }
        if (dao == null) {
            throw new AssertionError("DAO model is null");
        }

        DaoComparisonConfigLoader.DaoComparisonRule rule =
                configLoader.getRuleFor(apiResponse.getClass());

        if (rule == null) {
            throw new RuntimeException(
                    "No comparison rule found for " + apiResponse.getClass().getSimpleName());
        }

        // при желании можно проверить совпадение типа DAO
        if (!dao.getClass().getSimpleName().equals(rule.getDaoClassSimpleName())) {
            throw new RuntimeException("Unexpected DAO type: expected "
                    + rule.getDaoClassSimpleName() + " but was " + dao.getClass().getSimpleName());
        }

        Map<String, String> fieldMappings = rule.getFieldMappings();

        for (Map.Entry<String, String> mapping : fieldMappings.entrySet()) {
            String apiFieldName = mapping.getKey();
            String daoFieldName = mapping.getValue();

            Object apiValue = getFieldValue(apiResponse, apiFieldName);
            Object daoValue = getFieldValue(dao, daoFieldName);

            if (!Objects.equals(apiValue, daoValue)) {
                throw new AssertionError(String.format(
                        "Field mismatch for '%s': API=%s, DAO=%s",
                        apiFieldName, apiValue, daoValue
                ));
            }
        }
    }

    private Object getFieldValue(Object obj, String fieldPath) {
        try {
            String[] parts = fieldPath.split("\\.");
            Object current = obj;

            for (String part : parts) {
                if (current == null) {
                    return null;
                }
                Field field = current.getClass().getDeclaredField(part);
                field.setAccessible(true);
                current = field.get(current);
            }

            return current;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(
                    "Failed to get field value: " + fieldPath +
                            " from class " + obj.getClass().getSimpleName(), e);
        }
    }
}