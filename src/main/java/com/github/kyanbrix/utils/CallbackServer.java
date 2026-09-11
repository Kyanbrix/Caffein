package com.github.kyanbrix.utils;

import com.github.kyanbrix.config.database.UserRepository;
import io.javalin.Javalin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallbackServer {


    private static final Logger log = LoggerFactory.getLogger(CallbackServer.class);
    private static final String SUCCESS_TEMPLATE = TemplateLoader.loadTemplate("templates/callback-success.html");

    public void start() {

        Javalin.create(config -> {

            config.bundledPlugins.enableCors(corsPluginConfig -> corsPluginConfig.addRule(CorsPluginConfig.CorsRule::anyHost));
            config.routes.get("/api/lastfm/callback", ctx -> {

                String discordId = ctx.queryParam("discord_id");
                String token = ctx.queryParam("token");

                if (token == null ) {
                    ctx.status(400).result("Missing token");
                    return;

                }

                if (discordId == null) {
                    ctx.status(400).result("Missing Discord ID");
                    return;
                }

                try{

                    String[] sessionData = LastFmAuthService.getSessionInfo(token);
                    String htmlResponse = getString(sessionData, discordId);

                    ctx.contentType("text/html;charset=utf-8").html(htmlResponse);
                }catch (Exception e) {
                    ctx.status(500).result("Error Linking account");
                    log.warn("Failed to retrieve session info", e);
                }

            });


        }).start("0.0.0.0", 8080);

    }

    private static @NonNull String getString(String[] sessionData, String discordId) {
        String username = sessionData[0];
        String sessionKey = sessionData[1];
        String profileUrl = "https://www.last.fm/user/" + username;

        UserRepository userRepository = new UserRepository();

        userRepository.saveUserSession(Long.parseLong(discordId),username,sessionKey);

        return SUCCESS_TEMPLATE.replace("{{USERNAME}}", username).replace("{{PROFILE_URL}}", profileUrl);
    }


}
