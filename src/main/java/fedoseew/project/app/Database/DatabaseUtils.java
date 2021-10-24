package fedoseew.project.app.Database;

import fedoseew.project.app.Configurations.ApplicationConfiguration;
import org.hsqldb.util.DatabaseManagerSwing;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;


public final class DatabaseUtils {

    private static DatabaseUtils INSTANCE = null;
    private final EmbeddedDatabase db;

    private DatabaseUtils() {
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        db = builder
                .setType(EmbeddedDatabaseType.HSQL)
                .setName("db")
                .build();
    }

    public static DatabaseUtils createOrGetDatabase() {
        if (INSTANCE == null) {
            INSTANCE = new DatabaseUtils();
        }
        return INSTANCE;
    }

    public static String[] selectAllSourcesFromDb() {
        String[] result = new String[10];
        result[0] = "select source from " + DB_TABLES.source_zero;
        result[1] = "select source from " + DB_TABLES.source_one;
        result[2] = "select source from " + DB_TABLES.source_two;
        result[3] = "select source from " + DB_TABLES.source_three;
        result[4] = "select source from " + DB_TABLES.source_four;
        result[5] = "select source from " + DB_TABLES.source_five;
        result[6] = "select source from " + DB_TABLES.source_six;
        result[7] = "select source from " + DB_TABLES.source_seven;
        result[8] = "select source from " + DB_TABLES.source_eight;
        result[9] = "select source from " + DB_TABLES.source_nine;

        return result;
    }

    public static String[] selectAllFromDb() {
        String[] result = new String[10];
        result[0] = "select * from " + DB_TABLES.source_zero;
        result[1] = "select * from " + DB_TABLES.source_one;
        result[2] = "select * from " + DB_TABLES.source_two;
        result[3] = "select * from " + DB_TABLES.source_three;
        result[4] = "select * from " + DB_TABLES.source_four;
        result[5] = "select * from " + DB_TABLES.source_five;
        result[6] = "select * from " + DB_TABLES.source_six;
        result[7] = "select * from " + DB_TABLES.source_seven;
        result[8] = "select * from " + DB_TABLES.source_eight;
        result[9] = "select * from " + DB_TABLES.source_nine;

        return result;
    }

    public static synchronized void startDBManager() {
        String[] dbCredits = ApplicationConfiguration.getDatabaseCredits();
        DatabaseManagerSwing.main(dbCredits);

    }

    public Connection getConnection() throws SQLException {
        return db.getConnection();
    }

    public ResultSet selectQuery(String sql) {
        ResultSet resultSet = null;
        try {
            resultSet = getConnection().createStatement().executeQuery(sql);

        } catch (SQLException exception) {
            Logger.getGlobal().warning(exception.getSQLState() + exception.getLocalizedMessage());
            exception.printStackTrace();
        }
        return resultSet;
    }

    public void insertQuery(String sql) {
        try {
            getConnection().createStatement().execute(sql);
        } catch (SQLException exception) {
            Logger.getGlobal().warning(exception.getSQLState() + exception.getLocalizedMessage());
            exception.printStackTrace();
        }
    }
}
