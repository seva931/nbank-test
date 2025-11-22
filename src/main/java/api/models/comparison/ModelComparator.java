package api.models.comparison;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModelComparator {

    public static <A, B> ComparisonResult compareFields(A request,
                                                        B response,
                                                        Map<String, String> fieldMappings) {
        List<Mismatch> mismatches = new ArrayList<>();

        for (Map.Entry<String, String> e : fieldMappings.entrySet()) {
            String reqExpr = e.getKey();
            String respExpr = e.getValue();

            Object v1 = resolveValue(request, reqExpr);
            Object v2 = resolveValue(response, respExpr);

            if (!equalValues(v1, v2)) {
                mismatches.add(new Mismatch(reqExpr + " -> " + respExpr, v1, v2));
            }
        }
        return new ComparisonResult(mismatches);
    }

    private static Object resolveValue(Object root, String expr) {
        if (expr == null) return null;

        // 1) константа: const:Some Text
        if (expr.startsWith("const:")) {
            return expr.substring("const:".length());
        }

        // 2) путь вида a.b.c
        String[] parts = expr.split("\\.");
        Object current = root;

        for (String part : parts) {
            if (current == null) return null;
            current = reflectGet(current, part);
        }
        return current;
    }

    private static Object reflectGet(Object obj, String fieldName) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access field: " + fieldName, e);
            }
        }
        throw new RuntimeException("Field not found: " + fieldName + " in " + obj.getClass().getName());
    }

    private static boolean equalValues(Object v1, Object v2) {
        if (isNumericLike(v1) && isNumericLike(v2)) {
            BigDecimal b1 = toBigDecimal(v1);
            BigDecimal b2 = toBigDecimal(v2);
            return b1 != null && b2 != null && b1.compareTo(b2) == 0;
        }
        return Objects.equals(String.valueOf(v1), String.valueOf(v2));
    }

    private static boolean isNumericLike(Object o) {
        if (o == null) return false;
        if (o instanceof Number) return true;
        String s = String.valueOf(o).trim();
        return s.matches("[-+]?\\d+(\\.\\d+)?");
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    public static final class ComparisonResult {
        private final List<Mismatch> mismatches;

        public ComparisonResult(List<Mismatch> mismatches) {
            this.mismatches = mismatches;
        }

        public boolean isSuccess() {
            return mismatches.isEmpty();
        }

        public List<Mismatch> getMismatches() {
            return mismatches;
        }

        @Override
        public String toString() {
            if (isSuccess()) return "All fields match.";
            StringBuilder sb = new StringBuilder("Mismatched fields:\n");
            for (Mismatch m : mismatches) {
                sb.append("- ").append(m.fieldName)
                        .append(": expected=").append(m.expected)
                        .append(", actual=").append(m.actual).append("\n");
            }
            return sb.toString();
        }
    }

    public static final class Mismatch {
        public final String fieldName;
        public final Object expected;
        public final Object actual;

        public Mismatch(String fieldName, Object expected, Object actual) {
            this.fieldName = fieldName;
            this.expected = expected;
            this.actual = actual;
        }
    }
}
