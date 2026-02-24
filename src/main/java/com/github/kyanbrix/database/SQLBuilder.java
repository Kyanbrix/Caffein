package com.github.kyanbrix.database;

import com.github.kyanbrix.Caffein;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

public class SQLBuilder {

    private final List<Object> params = new ArrayList<>();
    private Connection connection;
    private final String sqlString;
    private boolean closeConnection = true;



    public SQLBuilder(String sqlString) {
        this.sqlString = sqlString;
    }

    public SQLBuilder addConnection(Connection connection) {
        this.connection = connection;
        closeConnection = false;
        return this;
    }

    public SQLBuilder addParameter(Object object) {
        this.params.add(object);
        return this;
    }

    public SQLBuilder addParameters(Object ... objects) {
        this.params.addAll(Arrays.asList(objects));
        return this;
    }

    public int executeUpdate() throws SQLException {
        Connection activeConnection = resolveConnection();

        try (PreparedStatement ps = activeConnection.prepareStatement(sqlString)) {
            bindParameters(ps);
            return ps.executeUpdate();
        } finally {
            if (closeConnection) {
                activeConnection.close();
            }
        }
    }



    public ResultSet executeQuery() throws SQLException {
        Connection activeConnection = resolveConnection();

        try (
                PreparedStatement ps = activeConnection.prepareStatement(sqlString);
                ResultSet rs = prepareAndExecuteQuery(ps)
        ) {
            CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
            rowSet.populate(rs);
            return rowSet;
        } finally {
            if (closeConnection) {
                activeConnection.close();
            }
        }
    }

    private Connection resolveConnection() throws SQLException {
        return connection == null ? Caffein.getInstance().getConnection() : connection;
    }

    private void bindParameters(PreparedStatement ps) throws SQLException {
        int i = 1;
        for (Object o : params) {
            ps.setObject(i, o);
            i++;
        }
    }

    private ResultSet prepareAndExecuteQuery(PreparedStatement ps) throws SQLException {
        bindParameters(ps);
        return ps.executeQuery();
    }
}
