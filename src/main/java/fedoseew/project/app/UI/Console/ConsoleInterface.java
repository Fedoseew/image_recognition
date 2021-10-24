package fedoseew.project.app.UI.Console;

import fedoseew.project.app.Configurations.ApplicationConfiguration;
import fedoseew.project.app.Database.DB_TABLES;
import fedoseew.project.app.Logic.utils.ConsoleImageParserToBinaryCode;
import fedoseew.project.app.Logic.ImageRecognition;

import java.io.*;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsoleInterface {

    private final BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in));

    private final BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(System.out));

    private final int sizeOfConsoleMatrix;

    public ConsoleInterface() {
        this.sizeOfConsoleMatrix = ApplicationConfiguration.getSizeOfGrid();
    }


    public void start() throws IOException, InterruptedException, SQLException {
        boolean flag = true;

        while (flag) {
            generateConsole();
            writer.write("Do you want to continue? YES - [1] \\ NO - [2]");
            writer.newLine();
            writer.flush();

            String response = reader.readLine();

            while (!(response.equals("1") || response.equals("2"))) {
                writer.write("Please, enter [1] or [2]");
                writer.newLine();
                writer.flush();
                response = reader.readLine();
            }
            if (response.equals("2")) {
                flag = false;
            }
        }
        writer.close();
        reader.close();

    }

    private void generateConsole() throws IOException, InterruptedException, SQLException {
        new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
        generateText();
        ConsoleImageParserToBinaryCode consoleImageParserToBinaryCode = new ConsoleImageParserToBinaryCode();
        ImageRecognition imageRecognition = new ImageRecognition();
        StringBuilder stringBuilder = new StringBuilder();
        int checkerForWrongInputText = 0;
        for (int i = 0; i < sizeOfConsoleMatrix; i++) {
            if (checkerForWrongInputText != i + 1) {
                stringBuilder.append(i + 1).append(" line: ");
                checkerForWrongInputText = i + 1;
            }
            writer.newLine();
            writer.write(stringBuilder.toString());
            writer.flush();
            String inputLine = reader.readLine();
            if (
                    (inputLine.length() > sizeOfConsoleMatrix || inputLine.length() < sizeOfConsoleMatrix)
                            || (!inputLine.contains("*") && !inputLine.contains(" "))
            ) {
                writer.write("\nEnter only \"*\" or \" \" (space) and length of input line must be "
                        + sizeOfConsoleMatrix + "\n");
                i--;
            } else {
                stringBuilder.append(inputLine);
                stringBuilder.append("\n");
            }
        }
        writer.write("\nPlease enter settings: \n");
        writer.write("ALPHA: ");
        writer.flush();
        String alpha = reader.readLine();
        writer.write("BETTA: ");
        writer.flush();
        String betta = reader.readLine();
        writer.write("GAMMA: ");
        writer.flush();
        String gamma = reader.readLine();
        writer.write("MIN METRIC: ");
        writer.flush();
        String minMetric = reader.readLine();
        Object[] settings = new Object[]{
                Integer.parseInt(alpha),
                Integer.parseInt(betta),
                Integer.parseInt(gamma),
                Double.parseDouble(minMetric)

        };
        Map<DB_TABLES, Integer> result = imageRecognition.recognition(
                consoleImageParserToBinaryCode
                        .consoleParserInputNumber(stringBuilder, sizeOfConsoleMatrix, writer), settings
        );
        AtomicInteger resultNumber = new AtomicInteger(-1);
        result.forEach((key, value) -> resultNumber.set(value));
        if (resultNumber.get() >= 0) {
            System.out.println("\nSuccessfully! Your number is " + resultNumber + ", right?");
        } else {
            System.out.println("\nThe recognition process is completed without a result :(" +
                    " Try a different number.");
        }
    }

    private void generateText() throws IOException {
        for (int i = 0; i < 5; i++) {
            writer.write("\n\033[H\033[2J\n");
        }
        writer.write("\n*****************************************\n");
        writer.write("Welcome to Image Recognition Application");
        writer.write("\n*****************************************\n");
        writer.write("\n To recognize the image, enter a number in the console ("
                + sizeOfConsoleMatrix + "x" + sizeOfConsoleMatrix + ").\n" +
                " ► Hint: Enter \"*\" or \" \" (space) ◄\n");
        writer.newLine();
        writer.write(" Start typing your number below:\n");
        writer.flush();
    }
}
