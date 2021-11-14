package fedoseew.project.app.Logic.utils;

import org.springframework.util.StringUtils;

import java.util.Map;

public final class ComplexIndGenerator {
    public static String generateComplexIndValue(String complexIndAlias, Map<Integer, String> indices) {
        Object[] arrayOfCharacters = complexIndAlias.split("\\|");
        String simpleInd1Value = indices.get(Integer.parseInt(StringUtils.replace((String) arrayOfCharacters[0], "X", "")));
        String simpleInd2Value = indices.get(Integer.parseInt(StringUtils.replace((String) arrayOfCharacters[1], "X", "")));
        String simpleInd3Value = indices.get(Integer.parseInt(StringUtils.replace((String) arrayOfCharacters[2], "X", "")));
        String simpleInd4Value = indices.get(Integer.parseInt(StringUtils.replace((String) arrayOfCharacters[3], "X", "")));
        String simpleInd5Value = indices.get(Integer.parseInt(StringUtils.replace((String) arrayOfCharacters[4], "X", "")));
        StringBuilder resultString = new StringBuilder();
        for (int chInd = 0; chInd < simpleInd1Value.length(); chInd++) {
            resultString
                    .append(simpleInd1Value.charAt(chInd))
                    .append(simpleInd2Value.charAt(chInd))
                    .append(simpleInd3Value.charAt(chInd))
                    .append(simpleInd4Value.charAt(chInd))
                    .append(simpleInd5Value.charAt(chInd));
            if (chInd != simpleInd1Value.length() - 1) {
                resultString.append(",");
            }
        }
        return resultString.toString();
    }
}
