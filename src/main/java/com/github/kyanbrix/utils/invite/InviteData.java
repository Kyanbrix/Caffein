package com.github.kyanbrix.utils.invite;

import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.User;

public class InviteData {

    private int numberOfUses;

    public InviteData(Invite invite) {
        this.numberOfUses = invite.getUses();
    }

    public int getNumberOfUses() {
        return numberOfUses;
    }



    public void incrementUses() {
        this.numberOfUses++;
    }
}
