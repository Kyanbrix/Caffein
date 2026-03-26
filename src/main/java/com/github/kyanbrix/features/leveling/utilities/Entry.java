package com.github.kyanbrix.features.leveling.utilities;

public class Entry {
    public final int    rank;
    public final String username;
    public final int    level;
    public final int    xp;
    public final int    xpNeeded;
    public final String avatarUrl;

    public Entry(int rank, String username, int level,
                 int xp, int xpNeeded, String avatarUrl) {
        this.rank      = rank;
        this.username  = username;
        this.level     = level;
        this.xp        = xp;
        this.xpNeeded  = xpNeeded;
        this.avatarUrl = avatarUrl;
    }
}
