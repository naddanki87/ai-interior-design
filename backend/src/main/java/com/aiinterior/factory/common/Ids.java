package com.aiinterior.factory.common;

import java.util.UUID;
import java.util.regex.Pattern;

public final class Ids {

    private static final Pattern SAFE_ID = Pattern.compile("^[a-z]{2,6}_[a-f0-9]{12}$");

    private Ids() {
    }

    public static String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Session ids become folder names under the export directory, so they must be path-safe. */
    public static boolean isSafe(String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }
}
