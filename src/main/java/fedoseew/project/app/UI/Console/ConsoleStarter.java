package fedoseew.project.app.UI.Console;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

public class ConsoleStarter {

    public static void main(String[] args) {
        startConsoleMode();
    }

    private static void startConsoleMode() {
        try {
            ConsoleInterface consoleInterface = new ConsoleInterface();
            consoleInterface.start();
        } catch (IOException | InterruptedException | SQLException exception) {
            Logger.getGlobal().warning("Console Interface starting error: \n");
            exception.printStackTrace();
        }
    }
}
