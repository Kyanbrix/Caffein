package com.github.kyanbrix.component.slashcommand;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.ParsingException;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /ioda: counts the Treasure Potions in a player's inventory and estimates how long
 * the Wheel of Doom takes to get them to the 1,000 an IoDA costs.
 */
public class Ioda implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(Ioda.class);

    private static final String CHAR_PAGE_URL = "https://account.aq.com/CharPage?id=";
    private static final String INVENTORY_URL = "https://account.aq.com/CharPage/Inventory?ccid=";
    private static final Pattern CCID = Pattern.compile("var ccid = (\\d+);");
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final int FETCH_TIMEOUT_MS = 15_000;

    private static final String POTION_NAME = "Treasure Potion";
    private static final int GOAL = 1000;
    private static final int BAR_LENGTH = 20;
    // Fortune Tickets are bought with ACs and give an extra spin
    private static final int TICKET_COST_ACS = 200;
    // Potions per spin, whether it's a daily, weekly or ticket spin
    private static final int POTIONS_PER_SPIN = 2;
    private static final int POTIONS_PER_SPIN_COMPLETED = 6;
    private static final Color EMBED_COLOR = new Color(0xF5C542);

    /**
     * A way of spinning the Wheel of Doom. Everyone gets the weekly free spin; members also
     * get a daily spin. Owning every wheel item raises the potions per spin from 2 to 6.
     */
    private record Plan(String label, boolean member, int potionsPerSpin) {

        int spinsPerWeek() {
            return member ? 8 : 1;
        }

        /**
         * Days from today until the last spin needed, assuming every spin is taken as soon as
         * it's available and both the daily and weekly spins are ready today.
         */
        int daysToFinish(int remaining) {
            int spinsNeeded = Math.ceilDiv(remaining, potionsPerSpin);
            if (!member) {
                return (spinsNeeded - 1) * 7;
            }

            int spins = 0;
            for (int day = 0; ; day++) {
                spins += day % 7 == 0 ? 2 : 1;
                if (spins >= spinsNeeded) {
                    return day;
                }
            }
        }
    }

    private static final List<Plan> PLANS = List.of(
            new Plan("Free Player", false, POTIONS_PER_SPIN),
            new Plan("Member", true, POTIONS_PER_SPIN),
            new Plan("Free Player (Wheel Completed)", false, POTIONS_PER_SPIN_COMPLETED),
            new Plan("Member (Wheel Completed)", true, POTIONS_PER_SPIN_COMPLETED));

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {
        String name = event.getOption("name", OptionMapping::getAsString);
        if (name == null || name.isBlank()) {
            event.reply("Please enter a character name.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        CompletableFuture.runAsync(() -> handle(event, name.trim()));
    }

    private void handle(SlashCommandInteraction event, String name) {
        String pageUrl = CHAR_PAGE_URL + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        Button viewButton = Button.of(ButtonStyle.LINK, pageUrl, "View Char Page");

        Document document;
        try {
            document = Jsoup.connect(pageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            log.warn("Failed to fetch char page for '{}': {}", name, e.getMessage());
            event.getHook().sendMessage("Couldn't reach the AQW char page right now. Try again in a bit.").queue();
            return;
        }

        // Missing, wiped or disabled characters get an alert box instead of the char page
        Element alert = document.getElementById("serveralert");
        if (alert != null) {
            String reason = alert.text().isBlank() ? "Not Found!" : alert.text().trim();
            event.getHook().sendMessage("**" + name + "**: " + reason).queue();
            return;
        }

        Matcher ccid = CCID.matcher(document.html());
        if (!ccid.find()) {
            event.getHook().sendMessage("Couldn't read that character's page.")
                    .addComponents(ActionRow.of(viewButton))
                    .queue();
            return;
        }

        Element header = document.selectFirst(".card-header h1");
        String displayName = header == null ? name : header.text().trim();

        DataArray inventory;
        try {
            String body = Jsoup.connect(INVENTORY_URL + ccid.group(1))
                    .userAgent(USER_AGENT)
                    .timeout(FETCH_TIMEOUT_MS)
                    .ignoreContentType(true)
                    .execute()
                    .body();
            inventory = DataArray.fromJson(body);
        } catch (IOException | ParsingException e) {
            log.warn("Failed to read inventory for '{}': {}", name, e.getMessage());
            event.getHook().sendMessage("Couldn't read **" + displayName + "**'s inventory right now.")
                    .addComponents(ActionRow.of(viewButton))
                    .queue();
            return;
        }

        // A player who hides their inventory gets a single placeholder entry with an intCharId of 0
        if (inventory.length() == 1 && inventory.getObject(0).getLong("intCharId", -1) == 0) {
            event.getHook().sendMessage("**" + displayName + "** has their inventory hidden.")
                    .addComponents(ActionRow.of(viewButton))
                    .queue();
            return;
        }

        event.getHook().sendMessageEmbeds(buildEmbed(displayName, pageUrl, countPotions(inventory)))
                .addComponents(ActionRow.of(viewButton))
                .queue();
    }

    private int countPotions(DataArray inventory) {
        int count = 0;
        for (int i = 0; i < inventory.length(); i++) {
            DataObject item = inventory.getObject(i);
            if (POTION_NAME.equalsIgnoreCase(item.getString("strName", ""))) {
                count += item.getInt("intCount", 0);
            }
        }
        return count;
    }

    private MessageEmbed buildEmbed(String name, String pageUrl, int potions) {
        int remaining = Math.max(0, GOAL - potions);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(name + "'s IoDA Progress", pageUrl)
                .setColor(EMBED_COLOR)
                .setDescription(String.format("**%s:** %,d / %,d%n%s %d%%",
                        POTION_NAME, potions, GOAL, progressBar(potions), Math.min(100, potions * 100 / GOAL)))
                .setFooter("Counts inventory only, not bank. Assumes every spin is taken starting today.");

        if (remaining == 0) {
            embed.addField("Ready!", "You already have enough Treasure Potions for an IoDA.", false);
            return embed.build();
        }

        embed.addField("Remaining", String.format("%,d potions", remaining), false);

        embed.addField("Buying Fortune Tickets",
                ticketCost("Normal", remaining, POTIONS_PER_SPIN) + "\n"
                        + ticketCost("Wheel Completed", remaining, POTIONS_PER_SPIN_COMPLETED), false);

        long now = Instant.now().getEpochSecond();
        for (Plan plan : PLANS) {
            int days = plan.daysToFinish(remaining);
            long finish = now + Duration.ofDays(days).toSeconds();

            String value = String.format("%d/spin · %d spin%s/week (%,d/week)%n**%s**%n<t:%d:D>",
                    plan.potionsPerSpin(),
                    plan.spinsPerWeek(),
                    plan.spinsPerWeek() == 1 ? "" : "s",
                    plan.potionsPerSpin() * plan.spinsPerWeek(),
                    formatDays(days),
                    finish);
            embed.addField(plan.label(), value, true);
        }

        return embed.build();
    }

    private String ticketCost(String label, int remaining, int potionsPerTicket) {
        int tickets = Math.ceilDiv(remaining, potionsPerTicket);
        return String.format("**%s** (%d/ticket): %,d tickets × %,d ACs = **%,d ACs**",
                label, potionsPerTicket, tickets, TICKET_COST_ACS, tickets * TICKET_COST_ACS);
    }

    private String progressBar(int potions) {
        int filled = Math.min(BAR_LENGTH, potions * BAR_LENGTH / GOAL);
        return "█".repeat(filled) + "░".repeat(BAR_LENGTH - filled);
    }

    private String formatDays(int days) {
        if (days == 0) {
            return "Today";
        }
        int weeks = days / 7;
        int rest = days % 7;
        StringBuilder out = new StringBuilder();
        if (weeks > 0) {
            out.append(weeks).append(weeks == 1 ? " week" : " weeks");
        }
        if (rest > 0) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(rest).append(rest == 1 ? " day" : " days");
        }
        return out.toString();
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("ioda", "Check your Treasure Potions and how long until an IoDA")
                .addOption(OptionType.STRING, "name", "Character name", true)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL)
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL);
    }
}
