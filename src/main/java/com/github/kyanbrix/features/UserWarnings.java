package com.github.kyanbrix.features;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class UserWarnings {

    private static final UserWarnings instance = new UserWarnings();

    public final Cache<Long, Integer> infractions = Caffeine.newBuilder()
            .build();


    private UserWarnings() {}

    public static UserWarnings getInstance() {
        return instance;
    }


    public int getInfractions(long userId) {

        return infractions.getIfPresent(userId);
    }



}
