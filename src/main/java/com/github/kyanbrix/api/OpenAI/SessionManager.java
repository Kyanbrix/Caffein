package com.github.kyanbrix.api.OpenAI;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatSession getOrCreate(String id) {

        return sessions.computeIfAbsent(id, ChatSession::new);

    }

    /** Forgets a channel's conversation, including the saved history. */
    public void reset(String id) {

        getOrCreate(id).clearHistory();

    }

}
