package com.github.kyanbrix.component.slashcommand;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kyanbrix.component.slashcommand.data.InviteInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.TimeFormat;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetGuildInviteInfo implements ISlash {

    private static final String API_ENDPOINT = "https://discord.com/api/v10";
    private static final Logger log = LoggerFactory.getLogger(GetGuildInviteInfo.class);
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    private static final Pattern INVITE_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?(?:discord\\.gg/|discord(?:app)?\\.com/invite/)([a-zA-Z0-9-]+)"
    );

    final OkHttpClient CLIENT = new OkHttpClient();

    private static final long DISCORD_EPOCH = 1420070400000L;
    private static final int MAX_ITEMS_PER_COL = 15;

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {

        final String inviteCode = event.getOption("invitecode", OptionMapping::getAsString);

        String extractedInviteCode = extractInviteCode(inviteCode);

        Request request = new Request.Builder()
                .addHeader("Authorization", "Bot "+System.getenv("DISCORD_TOKEN"))
                .get()
                .url(API_ENDPOINT+"/invites/"+extractedInviteCode)
                .build();


        try (Response response = CLIENT.newCall(request).execute()) {

            if (response.isSuccessful()) {


                InviteInfo info = mapper.readValue(response.body().string(), InviteInfo.class);

                String avatar_url = (info.guild.icon.startsWith("a_") ? String.format("https://cdn.discordapp.com/icons/%s/%s.gif",info.guild_id,info.guild.icon) : String.format("https://cdn.discordapp.com/icons/%s/%s.png",info.guild_id,info.guild.icon));

                String details = String.format("""
                        :stopwatch: Created: %s
                        <:ServerBoost:1543984173314744362> Level %d, %d boosts
                        <:Verified_green:1543986242436341905> Verification Level: **%s**
                        :link: Vanity URL: %s
                       
                        """,TimeFormat.RELATIVE.format(zonedDateTime(Long.parseLong(info.guild_id))),info.guild.premium_tier,info.guild.premium_subscription_count,
                        getVerificationLevel(info.guild.verification_level),(info.guild.vanity_url_code != null ? "https://discord.gg/"+info.guild.vanity_url_code : "None"));

                List<String> rawFeatures = info.guild.features;
                EmbedBuilder embed = new EmbedBuilder();

                List<String> formattedFeatures = new ArrayList<>();
                Collections.sort(rawFeatures);

                for(String feature: rawFeatures) {
                    formattedFeatures.add(getFeatureEmoji(feature) + " " + formatFeatureName(feature));
                }

                int chunkSize = MAX_ITEMS_PER_COL * 2;

                embed.setAuthor(info.guild.name, null, avatar_url);
                embed.setColor(Color.GREEN);
                embed.addField("Members", String.format("<:memberserver:1543958644092313711>%,d <a:matamison:1543958776888168570>%,d <:discord_offline:1543962189327507529>%,d", info.profile.member_count, info.profile.online_count, (info.profile.member_count - info.profile.online_count)), false);
                embed.addField("Details", details, false);
                embed.setImage((info.guild.banner == null ? null : ((info.guild.banner.startsWith("a_") ? String.format("https://cdn.discordapp.com/banners/%s/%s.gif?size=600", info.guild_id, info.guild.banner) : String.format("https://cdn.discordapp.com/banners/%s/%s.png?size=600", info.guild_id, info.guild.banner)))));
                embed.setDescription(info.guild.description == null || info.guild.description.isEmpty() ? "" : "\""+info.guild.description+"\"");

                for (int i=0; i<formattedFeatures.size(); i+=chunkSize) {
                    int end = Math.min(i + chunkSize, formattedFeatures.size());
                    List<String> chunk = formattedFeatures.subList(i, end);

                    int mid = (int) Math.ceil(chunk.size() / 2.0);
                    String col1 = String.join("\n", chunk.subList(0, mid));
                    String col2 = String.join("\n", chunk.subList(mid, chunk.size()));

                    String header1 = (i == 0) ? "Features (" + rawFeatures.size() + ")" : "\u200B";
                    embed.addField(header1, col1, true);
                    embed.addField("\u200B", col2.isEmpty() ? "\u200B" : col2, true);
                }



                event.replyEmbeds(embed.build()).addComponents(ActionRow.of(Button.of(ButtonStyle.LINK,inviteCode,"Accept Invite"))).queue();


            }else event.reply("Response Error "+ response.message() + "\nCode: "+response.code()).setEphemeral(true).queue();


        }catch (IOException e) {
            log.error(e.getMessage());
        }

    }


    private ZonedDateTime zonedDateTime(long epoch) {
        long millis = (epoch >> 22) + DISCORD_EPOCH;
        return Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Manila"));
    }

    @Override
    public @NonNull CommandData getCommandData() {
        return Commands.slash("inviteinfo","Get Guild Invite Information")
                .addOption(OptionType.STRING,"invitecode","Invite Code",true)
                .setIntegrationTypes(IntegrationType.GUILD_INSTALL,IntegrationType.USER_INSTALL)
                .setContexts(InteractionContextType.PRIVATE_CHANNEL,InteractionContextType.GUILD);
    }

    private String getVerificationLevel(int level) {

        return switch (level) {
            case 1 -> "LOW";
            case 2 -> "MEDIUM";
            case 3 -> "HIGH";
            default -> "UNKNOWN";
        };


    }


    private String extractInviteCode(String inviteUrl) {

        Matcher matcher = INVITE_PATTERN.matcher(inviteUrl);


        if (matcher.find()) {
            return matcher.group(1);
        }

        return inviteUrl;

    }

    private static String formatFeatureName(String feature) {
        String[] words = feature.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static String getFeatureEmoji(String feature) {

        return switch (feature) {

            case "ANIMATED_ICON" -> "🖼️";
            case "ANIMATED_BANNER" -> "";
            case "COMMUNITY" -> "🌐";
            case "SOUNDBOARD" -> "🎵";
            case "NEWS" -> "<:news_g:1544020866654801960>";
            case "INVITE_SPLASH" -> "🖼️";
            case "GUILD_ONBOARDING", "GUILD_ONBOARDING_HAS_PROMPTS" -> "🔍";
            case "ENHANCED_ROLE_COLORS" -> "👤";
            case "GUILD_TAGS" -> "⚔️";
            case "GUILD_ONBOARDING_EVER_ENABLED" -> "<a:enabled:1544021267017891920>";
            default -> "🔹";

        };

    }
}
