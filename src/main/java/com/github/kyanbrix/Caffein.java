package com.github.kyanbrix;

import com.github.kyanbrix.component.StringSelectionComponent;
import com.github.kyanbrix.component.command.CommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Properties;

public class Caffein {

    private static final Caffein INSTANCE = new Caffein();
    private JDA jda;

    public JDA getJda() {
        return jda;
    }

    private Caffein() {
    }

    public static Caffein getInstance() {
        return INSTANCE;
    }

    public static void main(String[] args) throws IOException {
        getInstance().start();
    }

    public void start() throws IOException {


        jda = JDABuilder.createLight(
                        getToken(),
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_PRESENCES,
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_INVITES
                )
                .disableCache(EnumSet.allOf(CacheFlag.class))
                .addEventListeners(new CommandManager(), new StringSelectionComponent(), new ButtonManager(), new MemberJoined(), new InviteTracker())
                .setEnableShutdownHook(true)
                .build();
    }

    private String getToken() throws IOException {
        String tokenFromEnv = System.getenv("DISCORD_TOKEN");
        if (tokenFromEnv != null && !tokenFromEnv.isBlank()) {
            return tokenFromEnv;
        }

        Properties properties = new Properties();
        try (InputStream input = Caffein.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                properties.load(input);
                String tokenFromResource = properties.getProperty("token");
                if (tokenFromResource != null && !tokenFromResource.isBlank()) {
                    return tokenFromResource;
                }
            }
        }

        try (FileInputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
            String tokenFromFile = properties.getProperty("token");
            if (tokenFromFile != null && !tokenFromFile.isBlank()) {
                return tokenFromFile;
            }
        } catch (IOException ignored) {
            // Fallback fails through to explicit error.
        }

        throw new IllegalStateException("No Discord token found. Set DISCORD_TOKEN environment variable.");
    }
}
