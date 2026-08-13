package io.github.miklires.mbans.database;

import java.util.Locale;

public enum StorageType {
    H2,
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRESQL;

    public static StorageType parse(String value) {
        if (value == null) return H2;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return H2;
        }
    }
}
