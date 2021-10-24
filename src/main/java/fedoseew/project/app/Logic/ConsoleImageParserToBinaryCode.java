package fedoseew.project.app.Logic;

import java.io.BufferedWriter;
import java.io.IOException;

public class ConsoleImageParserToBinaryCode {

    public String consoleParserInputNumber(StringBuilder stringBuilder, int sizeOfConsoleMatrix, BufferedWriter writer) throws IOException {
        String[] lines = stringBuilder.toString().split("\n");
        StringBuilder resultLine = new StringBuilder();
        writer.newLine();
        writer.write("Your number looks like this: \n");
        writer.newLine();
        writer.flush();
        for (int i = 0; i < sizeOfConsoleMatrix; i++) {
            lines[i] = lines[i].substring(8);
            System.out.println(lines[i]);
        }
        for (String s : lines) {
            for (int i = 0; i < s.length(); i++) {
                char character = s.charAt(i);
                if ('*' == character) {
                    resultLine.append(1);
                } else if (' ' == character) {
                    resultLine.append(0);
                }
            }
        }
        writer.newLine();
        writer.write("Representation in binary code: ");
        for (int i = 0; i < resultLine.length(); i++) {
            if (i != 0 && i % 4 == 0) {
                writer.write(" ");
            }
            writer.write(resultLine.charAt(i));
            writer.flush();
        }
        writer.write("\n");
        writer.flush();
        writer.newLine();
        return resultLine.toString();
    }
}
