package com.penmate.backend.infrastructure.persistence.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class LongListJsonbTypeHandler extends BaseTypeHandler<List<Long>> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() { };

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, List<Long> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            statement.setObject(index, JSON.writeValueAsString(parameter), Types.OTHER);
        } catch (Exception exception) {
            throw new SQLException("Failed to serialize chapter IDs", exception);
        }
    }

    @Override
    public List<Long> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return parse(resultSet.getString(columnName));
    }

    @Override
    public List<Long> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return parse(resultSet.getString(columnIndex));
    }

    @Override
    public List<Long> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return parse(statement.getString(columnIndex));
    }

    private List<Long> parse(String value) throws SQLException {
        if (value == null || value.isBlank()) return List.of();
        try {
            return List.copyOf(JSON.readValue(value, LONG_LIST));
        } catch (Exception exception) {
            throw new SQLException("Failed to deserialize chapter IDs", exception);
        }
    }
}
