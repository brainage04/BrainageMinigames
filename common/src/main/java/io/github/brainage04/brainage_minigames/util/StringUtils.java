package io.github.brainage04.brainage_minigames.util;

import java.util.List;

public class StringUtils {
    public static String camelCaseToHumanReadable(String input) {
        StringBuilder output = new StringBuilder(input.length());

        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char character = chars[i];
            if (i == 0) {
                output.append(Character.toUpperCase(character));
            } else {
                if (Character.isUpperCase(character)) {
                    output.append(' ');
                }
                output.append(character);
            }
        }

        return output.toString();
    }

    public static String classNameToSnakeCase(String input) {
        StringBuilder output = new StringBuilder(input.length());

        output.append(Character.toLowerCase(input.charAt(0)));

        for (int i = 1; i < input.length(); i++) {
            char currentChar = input.charAt(i);

            if (Character.isUpperCase(currentChar)) {
                output.append("_").append(Character.toLowerCase(currentChar));
            } else {
                output.append(currentChar);
            }
        }

        return output.toString();
    }

    public static String getWinnersTitle(List<String> winnerNames) {
        StringBuilder winnersList = new StringBuilder(winnerNames.getFirst());

        if (winnerNames.size() > 1) {
            for (int i = 1; i < winnerNames.size(); i++) {
                String winnerName = winnerNames.get(i);

                if (i == winnerNames.size() - 1) {
                    winnersList.append(" and ").append(winnerName);
                } else {
                    winnersList.append(", ").append(winnerName);
                }
            }
        }

        return winnersList.toString();
    }
}
