package fedoseew.project.app.Logic.utils;

import java.util.Arrays;
import java.util.Map;

public final class ComplexIndGenerator {
    public static String generateComplexIndValue(String complexIndAlias, Map<Integer, String> indices) {
        Object[] arrayOfCharacters = Arrays.stream(complexIndAlias.split("|")).filter(ch -> Character.isDigit(ch.charAt(0)) || ch.equals("|")).toArray();
        StringBuilder simpleInd1 = new StringBuilder();
        StringBuilder simpleInd2 = new StringBuilder();
        boolean forFirstInd = true;
        for (Object character : arrayOfCharacters) {
            if (character.equals("|")) {
                forFirstInd = false;
                continue;
            }
            if (forFirstInd) {
                simpleInd1.append(character);
            } else {
                simpleInd2.append(character);
            }
        }
        String simpleInd1Value = indices.get(Integer.parseInt(simpleInd1.toString()));
        String simpleInd2Value = indices.get(Integer.parseInt(simpleInd2.toString()));
        StringBuilder resultString = new StringBuilder();
        for (int chInd = 0; chInd < simpleInd1Value.length(); chInd++) {
            resultString
                    .append(simpleInd1Value.charAt(chInd))
                    .append(simpleInd2Value.charAt(chInd));
            if (chInd != simpleInd1Value.length() - 1) {
                resultString.append(",");
            }
        }
        return resultString.toString();
    }
}
