package com.github.kyanbrix.component.button;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

public class MaleButton implements IButton {
    private static final long MALE_ROLE_ID = 1474704365733548134L;

    @Override
    public void accept(ButtonInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();

        if (member == null || guild == null) {
            return;
        }

        Role maleRole = guild.getRoleById(MALE_ROLE_ID);
        if (maleRole == null) {
            sendReplyEphemeral(event, "Cannot update your profile because the role is missing in this server.");
            return;
        }

        List<Role> memberRoles = member.getRoles();
        if (memberRoles.contains(maleRole)) {
            removeRole(member, maleRole);
            sendReplyEphemeral(event, "I removed " + maleRole.getAsMention() + " from your profile");
            return;
        }

        guild.addRoleToMember(member, maleRole).queue();
        sendReplyEphemeral(event, "I added " + maleRole.getAsMention() + " to your profile");
    }

    private void removeRole(Member member, Role role) {
        member.getGuild().removeRoleFromMember(member, role).queue();
    }

    @Override
    public String buttonId() {
        return "he";
    }
}
