package com.ufi.voiceclient;

final class DialNumber {
    private DialNumber() {
    }

    static String normalize(String rawNumber) {
        StringBuilder normalized = new StringBuilder();
        String value = rawNumber == null ? "" : rawNumber.trim();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '0' && character <= '9') {
                normalized.append(character);
            } else if (character == '+' && normalized.length() == 0) {
                normalized.append(character);
            } else if (Character.isWhitespace(character) || character == '-'
                    || character == '(' || character == ')' || character == '.') {
                continue;
            } else {
                return "";
            }
        }
        int digitCount = normalized.length();
        if (digitCount > 0 && normalized.charAt(0) == '+') {
            digitCount--;
        }
        if (digitCount < 6 || digitCount > 20) {
            return "";
        }
        return normalized.toString();
    }
}
