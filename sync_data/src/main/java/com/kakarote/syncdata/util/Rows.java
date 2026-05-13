package com.kakarote.syncdata.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Rows {

    private static final Pattern DIGIT_SPLITTER = Pattern.compile("[^0-9]+");

    /**
     * 工具类不允许实例化。
     */
    private Rows() {
    }

    /**
     * 从行数据中读取 Long 类型字段，兼容数字和字符串格式。
     */
    public static Long longValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.parseLong(text);
    }

    /**
     * 从行数据中读取 Integer 类型字段，兼容数字和字符串格式。
     */
    public static Integer intValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Integer.parseInt(text);
    }

    /**
     * 从行数据中读取非空字符串，空白字符串会被转换为 null。
     */
    public static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    /**
     * 从行数据中读取字符串，字段为空时返回指定默认值。
     */
    public static String strOrDefault(Map<String, Object> row, String key, String defaultValue) {
        String value = str(row, key);
        return value == null ? defaultValue : value;
    }

    /**
     * 从行数据中读取时间字段，并转换为 JDBC Timestamp。
     */
    public static Timestamp timestamp(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof java.sql.Date date) {
            return new Timestamp(date.getTime());
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        if (value instanceof LocalDate localDate) {
            return Timestamp.valueOf(localDate.atStartOfDay());
        }
        String text = value.toString().trim();
        if (text.isEmpty() || "0000-00-00".equals(text) || text.startsWith("0000-00-00 ")) {
            return null;
        }
        return Timestamp.valueOf(text.length() == 10 ? text + " 00:00:00" : text);
    }

    /**
     * 返回第一个非空白字符串，并去除首尾空格。
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 使用指定分隔符拼接所有非空白字符串。
     */
    public static String joinNonBlank(String separator, String... values) {
        if (values == null) {
            return null;
        }
        String joined = java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(separator));
        return joined.isBlank() ? null : joined;
    }

    /**
     * 将字符串截断到目标库字段允许的最大长度。
     */
    public static String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 从混合文本中提取数字 ID，并按逗号拼接。
     */
    public static String parseIdList(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return DIGIT_SPLITTER.splitAsStream(value)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(","));
    }

    /**
     * 将字符串集合过滤空值后按逗号拼接。
     */
    public static String commaJoin(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }
}
