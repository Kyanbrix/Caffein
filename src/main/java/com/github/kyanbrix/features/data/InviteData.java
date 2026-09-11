package com.github.kyanbrix.features.data;

import net.dv8tion.jda.api.entities.Invite;

public class InviteData {

    private int numberOfUses;
    private final String url;


    public InviteData(Invite invite) {
        this.numberOfUses = invite.getUses();
        this.url = invite.getUrl();
    }

    public int getNumberOfUses() {
        return numberOfUses;
    }


    public String getUrl() {
        return url;
    }


    public void incrementUses() {
        this.numberOfUses++;
    }
}
