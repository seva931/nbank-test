package api.database;

import api.configs.Config;
import api.dao.AccountDao;
import api.dao.UserDao;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DBRequest {

    private RequestType requestType;
    private String table;
    private List<Condition> conditions = new ArrayList<>();

    public enum RequestType {
        SELECT
    }

    public static DBRequestBuilder builder() {
        return new DBRequestBuilder();
    }

    public static class DBRequestBuilder {
        private RequestType requestType;
        private String table;
        private List<Condition> conditions = new ArrayList<>();

        public DBRequestBuilder requestType(RequestType requestType) {
            this.requestType = requestType;
            return this;
        }

        public DBRequestBuilder table(String table) {
            this.table = table;
            return this;
        }

        public DBRequestBuilder where(Condition condition) {
            this.conditions.add(condition);
            return this;
        }

        public DBRequest build() {
            DBRequest request = new DBRequest();
            request.setRequestType(requestType);
            request.setTable(table);
            request.setConditions(conditions);
            return request;
        }

        public <T> T extractAs(Class<T> clazz) {
            DBRequest request = build();
            return request.executeQuery(clazz);
        }
    }

    private <T> T executeQuery(Class<T> clazz) {
        if (requestType != RequestType.SELECT) {
            throw new UnsupportedOperationException("Only SELECT is supported for now");
        }

        String sql = buildSQL();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // параметры для WHERE
            for (int i = 0; i < conditions.size(); i++) {
                statement.setObject(i + 1, conditions.get(i).getValue());
            }

            try (ResultSet rs = statement.executeQuery()) {
                if (clazz.equals(UserDao.class)) {
                    return (T) mapToUserDao(rs);
                }
                if (clazz.equals(AccountDao.class)) {
                    return (T) mapToAccountDao(rs);
                }
                throw new UnsupportedOperationException(
                        "Mapping for " + clazz.getSimpleName() + " is not implemented");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database query failed: " + e.getMessage(), e);
        }
    }

    private String buildSQL() {
        StringBuilder sql = new StringBuilder("SELECT * FROM ");
        sql.append(table);

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                Condition condition = conditions.get(i);
                sql.append(condition.getColumn())
                        .append(" ")
                        .append(condition.getOperator())
                        .append(" ?");
            }
        }

        return sql.toString();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                Config.getProperty("db.url"),
                Config.getProperty("db.username"),
                Config.getProperty("db.password")
        );
    }

    private UserDao mapToUserDao(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return UserDao.builder()
                    .id(rs.getLong("id"))
                    .username(rs.getString("username"))
                    .password(rs.getString("password"))
                    .role(rs.getString("role"))
                    .name(rs.getString("name"))
                    .build();
        }
        return null;
    }

    private AccountDao mapToAccountDao(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return AccountDao.builder()
                    .id(rs.getLong("id"))
                    .accountNumber(rs.getString("account_number"))
                    .balance(rs.getDouble("balance"))
                    .customerId(rs.getLong("customer_id"))
                    .build();
        }
        return null;
    }
}