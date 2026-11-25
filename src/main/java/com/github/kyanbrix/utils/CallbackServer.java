package com.github.kyanbrix.utils;

import com.github.kyanbrix.api.lastfm.LastFmAuthService;
import com.github.kyanbrix.api.lastfm.LastFmClient;
import com.github.kyanbrix.config.database.UserRepository;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.plugin.bundled.CorsPluginConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serves the Last.fm login callback on port 8080 (exposed through the Cloudflare tunnel). */
public class CallbackServer {


    private static final Logger log = LoggerFactory.getLogger(CallbackServer.class);
    private static final String SUCCESS_TEMPLATE = TemplateLoader.loadTemplate("templates/callback-success.html");
    private static final String ERROR_TEMPLATE = TemplateLoader.loadTemplate("templates/callback-error.html");

    public void start() {

        Javalin.create(config -> {

            config.bundledPlugins.enableCors(corsPluginConfig -> corsPluginConfig.addRule(CorsPluginConfig.CorsRule::anyHost));
            config.routes.get("/api/lastfm/callback", CallbackServer::handleLastFmCallback);

        }).start("0.0.0.0", 8080);

    }

    private static void handleLastFmCallback(Context ctx) {
        String token = ctx.queryParam("token");
        Long discordId = parseLong(ctx.queryParam("discord_id"));
        Long timestamp = parseLong(ctx.queryParam("ts"));
        String signature = ctx.queryParam("sig");

        if (token == null || token.isBlank() || discordId == null || timestamp == null) {
            error(ctx, 400, "Invalid link", "This link is missing some details. Run <code>/fm link</code> in Discord to get a new one.");
            return;
        }

        if (!LastFmAuthService.verifyState(discordId, timestamp, signature)) {
            error(ctx, 400, "Link expired", "This link has expired or isn't valid. Run <code>/fm link</code> in Discord to get a new one.");
            return;
        }

        try {
            LastFmAuthService.LastFmSession session = LastFmAuthService.getSessionInfo(token);
            new UserRepository().saveLastFmSession(discordId, session.username(), session.key());

            String username = escapeHtml(session.username());
            ctx.contentType("text/html;charset=utf-8").html(SUCCESS_TEMPLATE
                    .replace("{{USERNAME}}", username)
                    .replace("{{PROFILE_URL}}", escapeHtml(LastFmClient.profileUrl(session.username()))));
        } catch (Exception e) {
            log.warn("Failed to link Last.fm for Discord user {}", discordId, e);
            error(ctx, 500, "Couldn't link your account", "Something went wrong while talking to Last.fm. Run <code>/fm link</code> in Discord and try again.");
        }
    }

    /** The title is plain text; the message may contain trusted markup. */
    private static void error(Context ctx, int status, String title, String messageHtml) {
        ctx.status(status).contentType("text/html;charset=utf-8").html(ERROR_TEMPLATE
                .replace("{{TITLE}}", escapeHtml(title))
                .replace("{{MESSAGE}}", messageHtml));
    }

    private static Long parseLong(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escapeHtml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }


}
