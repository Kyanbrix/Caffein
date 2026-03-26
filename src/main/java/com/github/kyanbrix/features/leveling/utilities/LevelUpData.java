package com.github.kyanbrix.features.leveling.utilities;

public class LevelUpData {
    public final String username;
    public final int    oldLevel;
    public final int    newLevel;
    public final int    xpProgress;  // XP within new level
    public final int    xpNeeded;    // XP needed to reach next level
    public final int    rank;
    public final String avatarUrl;

    public LevelUpData(String username, int oldLevel, int newLevel, int xpProgress, int xpNeeded, int rank, String avatarUrl) {
        this.username = username;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.xpProgress = xpProgress;
        this.xpNeeded = xpNeeded;
        this.rank = rank;
        this.avatarUrl = avatarUrl;
    }
}
