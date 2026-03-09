package org.newsanalyzer.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JPA AttributeConverter for PostgreSQL text[] arrays.
 *
 * Handles conversion between Java List<String> and PostgreSQL text[] arrays,
 * bypassing Hibernate's Jackson-based deserialization which doesn't understand
 * PostgreSQL's array format {a,b,c}.
 *
 * This converter handles multiple input types from JDBC:
 * - java.sql.Array (standard PostgreSQL array)
 * - String[] (if JDBC driver already converted)
 * - Object[] (generic array)
 * - String (raw PostgreSQL array format like "{a,b,c}")
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, Object> {

    @Override
    public Object convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            // Return empty PostgreSQL array literal — will be cast via ?::text[]
            return "{}";
        }
        // Return PostgreSQL array literal format: {value1,"value with comma",value3}
        // The @ColumnTransformer(write = "?::text[]") on the entity field will cast
        // this String to text[] in the SQL, allowing PostgreSQL to parse the array literal.
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < attribute.size(); i++) {
            if (i > 0) sb.append(",");
            String val = attribute.get(i);
            if (val == null) {
                sb.append("NULL");
            } else {
                // Quote and escape the value for PostgreSQL array literal format
                sb.append("\"")
                  .append(val.replace("\\", "\\\\").replace("\"", "\\\""))
                  .append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public List<String> convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return new ArrayList<>();
        }

        // Handle java.sql.Array (most common from PostgreSQL)
        if (dbData instanceof Array) {
            try {
                Object arrayData = ((Array) dbData).getArray();
                if (arrayData instanceof String[]) {
                    return new ArrayList<>(Arrays.asList((String[]) arrayData));
                } else if (arrayData instanceof Object[]) {
                    List<String> result = new ArrayList<>();
                    for (Object obj : (Object[]) arrayData) {
                        result.add(obj != null ? obj.toString() : null);
                    }
                    return result;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to convert PostgreSQL array", e);
            }
        }

        // Handle String[] (if JDBC already converted)
        if (dbData instanceof String[]) {
            return new ArrayList<>(Arrays.asList((String[]) dbData));
        }

        // Handle Object[] (generic array)
        if (dbData instanceof Object[]) {
            List<String> result = new ArrayList<>();
            for (Object obj : (Object[]) dbData) {
                result.add(obj != null ? obj.toString() : null);
            }
            return result;
        }

        // Handle raw PostgreSQL array string format: {a,b,c}
        if (dbData instanceof String) {
            String str = (String) dbData;
            if (str.startsWith("{") && str.endsWith("}")) {
                str = str.substring(1, str.length() - 1);
                if (str.isEmpty()) {
                    return new ArrayList<>();
                }
                // Handle quoted values and commas within quotes
                return parsePostgresArray(str);
            }
            // Single value
            return new ArrayList<>(List.of(str));
        }

        throw new IllegalArgumentException(
                "Cannot convert database value of type " + dbData.getClass().getName() + " to List<String>");
    }

    /**
     * Parse PostgreSQL array string format, handling quoted values.
     * Examples: "a,b,c" or "\"value with, comma\",other"
     */
    private List<String> parsePostgresArray(String arrayContent) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current = new StringBuilder();
                continue;
            }

            current.append(c);
        }

        // Add the last element
        String lastElement = current.toString().trim();
        if (!lastElement.isEmpty() || !result.isEmpty()) {
            result.add(lastElement);
        }

        return result;
    }
}
