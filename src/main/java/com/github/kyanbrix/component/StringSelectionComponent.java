package com.github.kyanbrix.component;

import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public class StringSelectionComponent extends ListenerAdapter {

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        final String componentId = event.getComponentId();
        final int uniqueId = event.getUniqueId();


        switch (uniqueId) {
            case 2 -> handleDrinkSelection(event);
            case 1 -> {

                MessageComponentTree componentTree = event.getMessage().getComponentTree();

                Member member = event.getMember();
                List<Long> roleIds = member.getRoles().stream().map(ISnowflake::getIdLong).toList();
                Guild guild = event.getGuild();
                String value = event.getValues().getFirst();

                if (guild != null) {
                    if (value.equalsIgnoreCase("minor")) {
                        toggleRole(member,guild,roleIds,1474718646428368970L);
                    }
                    else if (value.equalsIgnoreCase("teens")) {
                        toggleRole(member,guild,roleIds,1474718736807235584L);
                    }
                    else if (value.equalsIgnoreCase("tito")) {
                        toggleRole(member,guild,roleIds,1474718785834586173L);

                    }

                    event.editComponents(componentTree).useComponentsV2().flatMap(interactionHook -> interactionHook.sendMessage("Your age role is now updated!").setEphemeral(true)).queue();

                }

            }
        }

    }

    private void handleDrinkSelection(StringSelectInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        List<String> values = event.getValues();
        List<Long> memberRoleIds = member.getRoles().stream().map(ISnowflake::getIdLong).toList();
        MessageComponentTree componentTree = event.getMessage().getComponentTree();

        for (String value : values) {
            switch (value) {
                case "esp" -> toggleRole(member, guild, memberRoleIds, 1474704831577854083L);
                case "cl" -> toggleRole(member, guild, memberRoleIds, 1474704867514781798L);
                case "cap" -> toggleRole(member, guild, memberRoleIds, 1474704917397770341L);
                case "cm" -> toggleRole(member,guild,memberRoleIds,1474704941556961451L);
                case "vl" -> toggleRole(member,guild,memberRoleIds,1474704967095816263L);
                case "mocha" -> toggleRole(member,guild,memberRoleIds,1474704997370298388L);
                case "ic" -> toggleRole(member,guild,memberRoleIds,1474705033257025709L);
                case "matchalatte" -> toggleRole(member,guild,memberRoleIds,1474705056006668349L);
                case "sm" -> toggleRole(member,guild,memberRoleIds,1474705078861697176L);
                case "cd" -> toggleRole(member,guild,memberRoleIds,1474705101087051899L);
                case "by" -> toggleRole(member,guild,memberRoleIds,1474705125099704411L);
                case "lem" -> toggleRole(member,guild,memberRoleIds,1474705145911709887L);
                case "mt" -> toggleRole(member,guild,memberRoleIds,1474705171610075219L);
                case "peach" -> toggleRole(member,guild,memberRoleIds,1474705209719787581L);
                case "hazel" -> toggleRole(member,guild,memberRoleIds,1474705232884928582L);
            }
        }

        event.editComponents(componentTree)
                .useComponentsV2()
                .flatMap(hook -> hook.sendMessage("Your server profile is now updated, check it out").setEphemeral(true))
                .queue();
    }

    private void toggleRole(Member member, Guild guild, List<Long> memberRoleIds, long roleId) {
        if (memberRoleIds.contains(roleId)) {
            guild.removeRoleFromMember(member, guild.getRoleById(roleId)).queue();
        } else {
            addRoleToMember(member, guild, roleId);
        }
    }

    private void addRoleToMember(Member member, Guild guild, long roleIdLong) {
        Role role = guild.getRoleById(roleIdLong);
        if (role != null) {
            guild.addRoleToMember(member, role).queue();
        } else {
            System.out.println("Role ID " + roleIdLong + " is not in the server");
        }
    }
}
