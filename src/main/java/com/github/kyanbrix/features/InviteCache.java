package com.github.kyanbrix.features;

import com.github.kyanbrix.features.data.InviteData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InviteCache {


    private static final InviteCache INSTANCE = new InviteCache();

    private final Map<String, InviteData> inviteCache = new ConcurrentHashMap<>();

    private InviteCache() {}

    public static InviteCache getInstance() {
        return INSTANCE;
    }

    public Map<String, InviteData> getInviteCache() {
        return inviteCache;
    }


    public InviteData getInviteData(String inviteId) {
        return  inviteCache.get(inviteId);
    }


    public void setInviteCache(String inviteCode, InviteData inviteData) {
        inviteCache.put(inviteCode, inviteData);
    }

    public void removeInviteCache(String inviteCode) {
        inviteCache.remove(inviteCode);
    }





}
