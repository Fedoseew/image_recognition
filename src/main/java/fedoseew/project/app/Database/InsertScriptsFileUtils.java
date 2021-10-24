package fedoseew.project.app.Database;

import fedoseew.project.app.Configurations.ApplicationConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;


public class InsertScriptsFileUtils {

    public static List<String> readInsertScriptsFile() throws IOException {
        return Files.readAllLines(Paths.get(ApplicationConfiguration.getPathToInsertScripts()), StandardCharsets.UTF_8);
    }

    public synchronized static void deleteSourceFromInsertScriptsFile(String source, DB_TABLES table, boolean isTrue) throws IOException {
        String deletedRow = "INSERT INTO " + table + " VALUES ('" + source + "', " + isTrue + ");";
        List<String> lines = readInsertScriptsFile();
        int indexOfUpdateFromRow = lines.indexOf(deletedRow) - 1;
        String updatedFromRow = lines.get(indexOfUpdateFromRow);
        if (updatedFromRow.contains("--UPDATED FROM")) {
            lines.remove(updatedFromRow);
        }
        lines.remove(deletedRow);
        Files.write(Paths.get(ApplicationConfiguration.getPathToInsertScripts()), lines);

    }

    public synchronized static void writeInsertScriptsFile(String source, DB_TABLES table, boolean isTrue) throws IOException {
        String SQL = "INSERT INTO " + table + " VALUES ('" + source + "', " + isTrue + ");";
        StringBuilder oldScripts = new StringBuilder();
        boolean flag = true;
        for (String row : readInsertScriptsFile()) {
            if (row.contains(table.toString()) && row.contains(String.valueOf(isTrue)) && flag) {
                oldScripts.append("--UPDATED FROM ").append(LocalDateTime.now()).append("--\n");
                oldScripts.append(SQL).append("\n");
                flag = false;
            }
            oldScripts.append(row).append("\n");
        }
        Files.write(Paths.get(ApplicationConfiguration.getPathToInsertScripts()), oldScripts.toString().getBytes(StandardCharsets.UTF_8)
        );
    }
}
