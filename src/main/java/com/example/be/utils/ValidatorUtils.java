package com.example.be.utils;

public class ValidatorUtils {

    public static boolean isValidString(String str) {
        return str != null && !str.isBlank();
    }
}
