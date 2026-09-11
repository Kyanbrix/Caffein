package com.github.kyanbrix.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

public class LastFmSignature {
    public static String sign(Map<String, String> params, String apiSecret) {

        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.remove("format"); // NEVER include format in the signature

        StringBuilder base = new StringBuilder();
        sorted.forEach((k, v) -> base.append(k).append(v));
        base.append(apiSecret);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(base.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 hashing failed", e);
        }
    }
}
