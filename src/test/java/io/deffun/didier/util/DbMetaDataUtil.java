package io.deffun.didier.util;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class DbMetaDataUtil {
    private DbMetaDataUtil() {
    }

    public static Map<String, ColumnMetaData> collectColumns(DatabaseMetaData databaseMetaData, String tableNamePattern) throws SQLException {
        Map<String, ColumnMetaData> result = new HashMap<>();
        try (ResultSet columns = databaseMetaData.getColumns(null, null, tableNamePattern, null)) {
            while (columns.next()) {
                result.put(columns.getString("COLUMN_NAME"), new ColumnMetaData(columns.getString("TYPE_NAME"), columns.getBoolean("IS_NULLABLE"), columns.getBoolean("IS_AUTOINCREMENT")));
            }
        }
        return result;
    }
}
