package com.github.kyanbrix.features.leveling.utilities;

public class Data {
    public final String username;
    public final int    level;
    public final int    xpProgress;   // XP within current level
    public final int    xpNeeded;     // XP needed to reach next level
    public final int    rank;
    public final String avatarUrl;

    public Data(String username, int level, int xpProgress,
                int xpNeeded, int rank, String avatarUrl) {
        this.username    = username;
        this.level       = level;
        this.xpProgress  = xpProgress;
        this.xpNeeded    = xpNeeded;
        this.rank        = rank;
        this.avatarUrl   = avatarUrl;
    }


}
