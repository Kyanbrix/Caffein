package com.github.kyanbrix.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class TemplateLoader {


    public static String loadTemplate(String path)
    {
        try (InputStream is = TemplateLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Template not found: " + path);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load HTML template", e);
        }

    }
}
