package io.smallrye.modules.jfr;

public final class JfrSupport {
    private JfrSupport() {
    }

    public static final boolean ENABLED;

    static {
        ENABLED = Boolean.parseBoolean(System.getProperty("io.smallrye.modules.jfr", "false"));
    }
}
