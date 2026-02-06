package io.github.flameyossnowy.universal.api;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModelsBootstrap {
    private static final String RESOURCE =
        "META-INF/universal/models.list";

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        try (InputStream in = cl.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("No generated object models found");
            }

            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println(line);
                    Class.forName(line, true, cl);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load object models", e);
        }
    }
}
