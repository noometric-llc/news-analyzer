package org.newsanalyzer.model.converter;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hibernate UserType for PostgreSQL text[] arrays.
 *
 * Unlike JPA's AttributeConverter, UserType has access to the PreparedStatement
 * and Connection, allowing us to create proper java.sql.Array objects for writes
 * and handle java.sql.Array results on reads.
 *
 * This solves the PostgreSQL text[] binding problem where AttributeConverter
 * could not create java.sql.Array (needed Connection access) and returning
 * String[] or String literals failed JDBC parameter binding.
 */
public class StringListUserType implements UserType<List<String>> {

    @Override
    public int getSqlType() {
        // Use Types.OTHER instead of Types.ARRAY to prevent Hibernate 6 from
        // trying to wrap this in BasicPluralType (which causes ClassCastException).
        // The actual JDBC array handling is done in nullSafeSet/nullSafeGet.
        return Types.OTHER;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<String>> returnedClass() {
        return (Class<List<String>>) (Class<?>) List.class;
    }

    @Override
    public boolean equals(List<String> x, List<String> y) {
        if (x == y) return true;
        if (x == null || y == null) return false;
        return x.equals(y);
    }

    @Override
    public int hashCode(List<String> x) {
        return x != null ? x.hashCode() : 0;
    }

    @Override
    public List<String> nullSafeGet(ResultSet rs, int position,
                                     SharedSessionContractImplementor session,
                                     Object owner) throws SQLException {
        Array array = rs.getArray(position);
        if (rs.wasNull() || array == null) {
            return new ArrayList<>();
        }

        Object arrayData = array.getArray();
        if (arrayData instanceof String[]) {
            return new ArrayList<>(Arrays.asList((String[]) arrayData));
        } else if (arrayData instanceof Object[]) {
            List<String> result = new ArrayList<>();
            for (Object obj : (Object[]) arrayData) {
                result.add(obj != null ? obj.toString() : null);
            }
            return result;
        }

        return new ArrayList<>();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, List<String> value, int index,
                             SharedSessionContractImplementor session) throws SQLException {
        if (value == null || value.isEmpty()) {
            // Create an empty PostgreSQL text array (not NULL — preserves empty vs absent semantics)
            Connection conn = st.getConnection();
            Array emptyArray = conn.createArrayOf("text", new String[0]);
            st.setArray(index, emptyArray);
        } else {
            Connection conn = st.getConnection();
            Array array = conn.createArrayOf("text", value.toArray(new String[0]));
            st.setArray(index, array);
        }
    }

    @Override
    public List<String> deepCopy(List<String> value) {
        return value != null ? new ArrayList<>(value) : null;
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(List<String> value) {
        return value != null ? new ArrayList<>(value) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> assemble(Serializable cached, Object owner) {
        return cached != null ? new ArrayList<>((List<String>) cached) : null;
    }
}
