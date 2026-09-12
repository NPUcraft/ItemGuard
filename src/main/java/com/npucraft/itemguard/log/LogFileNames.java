package com.npucraft.itemguard.log;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogFileNames {

    private static final Pattern FILE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})(?:-(\\d+))?\\.jsonl$");

    private LogFileNames() {
    }

    public static String fileName(LocalDate date, int index) {
        if (index <= 0) {
            return date + ".jsonl";
        }
        return date + "-" + index + ".jsonl";
    }

    public static boolean isManagedLog(String fileName) {
        return fileName != null && FILE.matcher(fileName).matches();
    }

    public static LocalDate dateOf(String fileName) {
        Matcher matcher = FILE.matcher(fileName == null ? "" : fileName);
        if (!matcher.matches()) {
            return null;
        }
        return LocalDate.parse(matcher.group(1));
    }

    public static int indexOf(String fileName) {
        Matcher matcher = FILE.matcher(fileName == null ? "" : fileName);
        if (!matcher.matches()) {
            return -1;
        }
        String index = matcher.group(2);
        return index == null ? 0 : Integer.parseInt(index);
    }
}
