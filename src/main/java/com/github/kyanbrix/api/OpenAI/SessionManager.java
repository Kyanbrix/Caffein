package com.github.kyanbrix.api.OpenAI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatSession getOrCreate(String id) {

        return sessions.computeIfAbsent(id, k -> new ChatSession());

    }



}
