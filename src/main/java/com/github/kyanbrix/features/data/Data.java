package com.github.kyanbrix.features.data;

/**
 * @param xpProgress XP within current level
 * @param xpNeeded   XP needed to reach next level
 */
public record Data(String username, int level, int xpProgress, int xpNeeded, int rank, String avatarUrl) {


}
