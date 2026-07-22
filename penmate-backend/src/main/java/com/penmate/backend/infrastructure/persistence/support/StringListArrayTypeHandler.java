package com.penmate.backend.infrastructure.persistence.support;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class StringListArrayTypeHandler extends BaseTypeHandler<List<String>> {

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, List<String> parameter,
                                    JdbcType jdbcType) throws SQLException {
        Connection connection = statement.getConnection();
        Array array = connection.createArrayOf("varchar", parameter.toArray(String[]::new));
        statement.setArray(index, array);
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return values(resultSet.getArray(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return values(resultSet.getArray(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
        return values(statement.getArray(columnIndex));
    }

    private List<String> values(Array array) throws SQLException {
        if (array == null) return List.of();
        try {
            Object raw = array.getArray();
            if (raw instanceof String[] values) return List.copyOf(Arrays.asList(values));
            if (raw instanceof Object[] values) return Arrays.stream(values).map(String::valueOf).toList();
            return List.of();
        } finally {
            array.free();
        }
    }
}
