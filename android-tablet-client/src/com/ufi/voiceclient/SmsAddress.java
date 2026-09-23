package com.ufi.voiceclient;

final class SmsAddress {
    private SmsAddress() {
    }

    static String normalize(String rawAddress) {
        String value = rawAddress == null ? "" : rawAddress.trim();
        StringBuilder normalized = new StringBuilder();
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
        int digits = normalized.length();
        if (digits > 0 && normalized.charAt(0) == '+') {
            digits--;
        }
        return digits >= 3 && digits <= 20 ? normalized.toString() : "";
    }
}
