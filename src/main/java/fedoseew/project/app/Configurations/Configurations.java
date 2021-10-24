package fedoseew.project.app.Configurations;

public abstract class Configurations {

    protected final static String PATH_TO_CREATE_SCRIPT = "./src/main/resources/fedoseew/project/app/db/create.sql";
    protected final static String PATH_TO_INSERT_SCRIPT = "./src/main/resources/fedoseew/project/app/db/insert.sql";
    protected final static String[] DATABASE_CREDIT = new String[]{
            "--url", "jdbc:hsqldb:mem:db",
            "--user", "sa", "--password", ""
    };
    protected static int SIZE_OF_GRID = 5;

}
