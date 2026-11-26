package com.github.kyanbrix.component.slashcommand;

import com.github.kyanbrix.api.lastfm.LastFmAuthService;
import com.github.kyanbrix.api.lastfm.LastFmClient;
import com.github.kyanbrix.api.lastfm.LastFmPeriod;
import com.github.kyanbrix.api.lastfm.LastFmRecentTracksResponse;
import com.github.kyanbrix.api.lastfm.LastFmTopTracksResponse;
import com.github.kyanbrix.api.lastfm.LastFmTrackInfoResponse;
import com.github.kyanbrix.component.slashcommand.data.TrackMetadata;
import com.github.kyanbrix.component.slashcommand.responses.LrcLibResponse;
import com.github.kyanbrix.utils.Constant;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the Components V2 messages for /fm; no network calls happen here. */
final class LastFmViews {

    private static final String NOW_PLAYING_EMOJI = "<a:emojigg_cd:1548026549175853067>";
    private static final String STATS_EMOJI = "<:Stats:1545648322805637132>";
    private static final String[] MEDALS = {"🥇", "🥈", "🥉"};

    /** Track and artist names are cut to this; a few are absurdly long. */
    private static final int NAME_LIMIT = 100;
    /**
     * Every text in a components message shares a 4000 character budget. A lyrics
     * page plus its header and escaping stays well under it.
     */
    static final int LYRICS_PAGE_LIMIT = 1800;

    private LastFmViews() {}

    // ---- now playing ----

    /**
     * @param info track.getInfo for the track, or null if that call failed; its
     *             fields (plays, loved, album, duration) are then left out
     */
    static Container nowPlaying(String username, LastFmRecentTracksResponse.Track track,
                                LastFmTrackInfoResponse.Track info, TrackMetadata metadata, long totalScrobbles) {
        StringBuilder text = new StringBuilder("-# ");
        if (track.isNowPlaying()) {
            text.append(NOW_PLAYING_EMOJI).append(" Now playing");
        } else {
            Long scrobbledAt = track.getScrobbledAtEpochSecond();
            text.append("🕒 Last played");
            if (scrobbledAt != null) {
                text.append(' ').append(TimeFormat.RELATIVE.format(scrobbledAt * 1000));
            }
        }
        text.append(" · ").append(profileLink(username));

        String artist = track.getArtist().getName();
        text.append("\n## ").append(link(track.getName(), track.getUrl()));
        text.append("\n**").append(escape(artist)).append("**");

        String album = track.getAlbum() != null ? track.getAlbum().getTitle() : null;
        if ((album == null || album.isBlank()) && info != null && info.getAlbum() != null) {
            album = info.getAlbum().getTitle();
        }
        if (album != null && !album.isBlank()) {
            text.append(" · *").append(escape(album)).append('*');
        }

        List<String> details = new ArrayList<>();
        if (info != null) {
            if (info.getDurationMs() > 0) details.add(formatDuration(info.getDurationMs()));
            if (info.getUserPlayCount() >= 0) details.add(plays(info.getUserPlayCount()));
            if (info.isLoved()) details.add("❤️ Loved");
        }
        if (!details.isEmpty()) {
            text.append("\n-# ").append(String.join(" · ", details));
        }

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(withArtwork(metadata.artworkUrl(), text.toString()));
        components.add(Separator.createDivider(Separator.Spacing.LARGE));
        components.add(TextDisplay.of("-# " + number(totalScrobbles) + " scrobbles in total"));
        return Container.of(components).withAccentColor(metadata.accentColor());
    }

    // ---- top tracks ----

    /**
     * One page of top tracks with the period menu and pager.
     *
     * @param artwork the metadata for each track in {@code tracks}, in order
     */
    static Container topTracks(long ownerId, long targetId, String username, LastFmPeriod period,
                               int page, int totalPages, long totalTracks,
                               List<LastFmTopTracksResponse.Track> tracks, List<TrackMetadata> artwork) {
        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of("## " + STATS_EMOJI + " Top tracks · " + period.label()
                + "\n-# " + profileLink(username) + " · " + period.description()
                + " · " + number(totalTracks) + (totalTracks == 1 ? " track" : " tracks")));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));

        if (tracks.isEmpty()) {
            components.add(TextDisplay.of("Nothing scrobbled in this period yet. Pick another one below."));
        }

        int firstRank = (page - 1) * LastFm.TRACKS_PER_PAGE + 1;
        for (int i = 0; i < tracks.size(); i++) {
            if (i > 0) {
                components.add(Separator.createInvisible(Separator.Spacing.SMALL));
            }

            LastFmTopTracksResponse.Track track = tracks.get(i);
            int rank = firstRank + i;
            String rankLabel = rank <= MEDALS.length ? MEDALS[rank - 1] : rank + ".";

            String text = "### " + rankLabel + " " + link(track.getName(), track.getUrl())
                    + "\n**" + escape(track.getArtist().getName()) + "**"
                    + "\n-# " + plays(parseCount(track.getPlaycount()));
            components.add(withArtwork(artwork.get(i).artworkUrl(), text));
        }

        components.add(Separator.createDivider(Separator.Spacing.LARGE));
        components.add(periodMenu(ownerId, targetId, period));
        if (totalPages > 1) {
            String idPrefix = LastFm.BUTTON_PREFIX + ":top:%s:" + ownerId + ":" + targetId + ":" + period.code() + ":";
            components.add(pager(idPrefix, page, totalPages));
        }

        // The top track's colour, so the card matches the first thing you see
        return Container.of(components)
                .withAccentColor(artwork.isEmpty() ? Constant.LAST_FM_RED : artwork.getFirst().accentColor());
    }

    // ---- top artists ----

    /**
     * The top artists card: the header and the grid image from {@link TopArtistsGrid}.
     * It has no controls; the command's options decide the period and how many artists.
     *
     * @param image  the rendered grid, or null when the period has no artists
     * @param accent the #1 artist's photo colour
     */
    static Container topArtists(String username, LastFmPeriod period, long totalArtists,
                                List<TopArtistsGrid.Tile> tiles, FileUpload image, Color accent) {
        String shown = tiles.isEmpty() || tiles.size() >= totalArtists ? ""
                : "Top " + tiles.size() + " of ";
        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of("## " + STATS_EMOJI + " Top artists · " + period.label()
                + "\n-# " + profileLink(username) + " · " + period.description()
                + " · " + shown + number(totalArtists) + (totalArtists == 1 ? " artist" : " artists")));

        if (image == null) {
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
            components.add(TextDisplay.of("Nothing scrobbled in this period yet. Try a longer period."));
        } else {
            components.add(MediaGallery.of(MediaGalleryItem.fromFile(image).withDescription(altText(tiles))));
        }

        return Container.of(components).withAccentColor(accent);
    }

    /** Screen-reader text for the grid: "1. Artist (1,234 plays), 2. …". */
    private static String altText(List<TopArtistsGrid.Tile> tiles) {
        List<String> entries = new ArrayList<>();
        for (TopArtistsGrid.Tile tile : tiles) {
            entries.add(tile.rank() + ". " + tile.name() + " (" + plays(tile.plays()) + ")");
        }
        String text = String.join(", ", entries);
        int limit = MediaGalleryItem.MAX_DESCRIPTION_LENGTH;
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }

    private static ActionRow periodMenu(long ownerId, long targetId, LastFmPeriod current) {
        List<SelectOption> options = new ArrayList<>();
        for (LastFmPeriod period : LastFmPeriod.values()) {
            options.add(SelectOption.of(period.label(), period.code())
                    .withDescription(period.description())
                    .withDefault(period == current));
        }

        return ActionRow.of(StringSelectMenu.create(LastFm.PERIOD_SELECT_PREFIX + LastFm.TRACKS_MENU_KEY + ":" + ownerId + ":" + targetId)
                .setPlaceholder("Change period…")
                .addOptions(options)
                .build());
    }

    // ---- lyrics ----

    static Container lyrics(long ownerId, LrcLibResponse song, TrackMetadata metadata, int requestedPage) {
        StringBuilder header = new StringBuilder("## ").append(escape(song.trackName))
                .append("\n**").append(escape(song.artistName)).append("**");
        if (song.albumName != null && !song.albumName.isBlank() && !song.albumName.equalsIgnoreCase(song.trackName)) {
            header.append(" · *").append(escape(song.albumName)).append('*');
        }

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(withArtwork(metadata.artworkUrl(), header.toString()));
        components.add(Separator.createDivider(Separator.Spacing.SMALL));

        List<String> pages = song.plainLyrics == null ? List.of() : lyricsPages(song.plainLyrics);
        int page = pages.isEmpty() ? 1 : Math.clamp(requestedPage, 1, pages.size());

        if (pages.isEmpty()) {
            components.add(TextDisplay.of("*This track is instrumental.*"));
        } else {
            components.add(TextDisplay.of(escapeLyrics(pages.get(page - 1))));
        }

        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        components.add(TextDisplay.of("-# Lyrics from LRCLIB"));
        if (pages.size() > 1) {
            components.add(pager(LastFm.BUTTON_PREFIX + ":lyr:%s:" + ownerId + ":" + song.id + ":", page, pages.size()));
        }

        return Container.of(components).withAccentColor(metadata.accentColor());
    }

    /** Splits lyrics into pages, breaking between verses where it can, then between lines. */
    static List<String> lyricsPages(String lyrics) {
        List<String> pages = new ArrayList<>();
        String rest = lyrics.strip();

        while (!rest.isEmpty()) {
            if (rest.length() <= LYRICS_PAGE_LIMIT) {
                pages.add(rest);
                break;
            }

            int split = rest.lastIndexOf("\n\n", LYRICS_PAGE_LIMIT);
            // Only break on a verse if it doesn't leave the page mostly empty
            if (split < LYRICS_PAGE_LIMIT / 2) {
                split = rest.lastIndexOf('\n', LYRICS_PAGE_LIMIT);
            }
            if (split <= 0) {
                split = LYRICS_PAGE_LIMIT;
            }

            pages.add(rest.substring(0, split).strip());
            rest = rest.substring(split).strip();
        }

        return pages;
    }

    // ---- linking ----

    /** The ephemeral card with the Last.fm login button. */
    static Container linkPrompt(long discordId, String currentUsername) {
        String minutes = String.valueOf(LastFmAuthService.LINK_LIFETIME.toMinutes());
        String text;
        String buttonLabel;
        if (currentUsername == null) {
            text = "## Connect Last.fm"
                    + "\nLink your Last.fm account to use `/fm now`, `/fm top tracks` and `/fm lyrics`."
                    + "\n-# Last.fm will ask you to allow access. The button works for " + minutes + " minutes.";
            buttonLabel = "Connect Last.fm";
        } else {
            text = "## Last.fm account"
                    + "\nYou're linked as " + profileLink(currentUsername) + "."
                    + "\nTo switch accounts, log into the other one on Last.fm and press the button."
                    + "\n-# The button works for " + minutes + " minutes. `/fm unlink` removes the link.";
            buttonLabel = "Link a different account";
        }

        return Container.of(
                TextDisplay.of(text),
                ActionRow.of(Button.link(LastFmAuthService.createAuthUrl(discordId), buttonLabel))
        ).withAccentColor(Constant.LAST_FM_RED);
    }

    // ---- shared ----

    /**
     * First / previous / page count / next / last. Each action gets its own ID
     * since Discord needs IDs unique within a message, and on page 2 both
     * "first" and "previous" point at page 1.
     *
     * @param idFormat a custom ID with {@code %s} where the action goes; the page number is appended
     */
    private static ActionRow pager(String idFormat, int page, int totalPages) {
        return ActionRow.of(
                Button.secondary(String.format(idFormat, "first") + 1, Emoji.fromUnicode("U+23EA")).withDisabled(page == 1),
                Button.secondary(String.format(idFormat, "prev") + (page - 1), "Previous").withDisabled(page == 1),
                Button.secondary(String.format(idFormat, "page") + page, number(page) + " / " + number(totalPages)).withDisabled(true),
                Button.secondary(String.format(idFormat, "next") + (page + 1), "Next").withDisabled(page >= totalPages),
                Button.secondary(String.format(idFormat, "last") + totalPages, Emoji.fromUnicode("U+23E9")).withDisabled(page >= totalPages));
    }

    private static ContainerChildComponent withArtwork(String artworkUrl, String text) {
        return artworkUrl == null
                ? TextDisplay.of(text)
                : Section.of(Thumbnail.fromUrl(artworkUrl), TextDisplay.of(text));
    }

    private static String profileLink(String username) {
        return "[" + escape(username) + "](" + LastFmClient.profileUrl(username) + ")";
    }

    /** A masked link whose text can't break out of the brackets. */
    private static String link(String text, String url) {
        String label = escape(text).replace("[", "\\[").replace("]", "\\]");
        if (url == null) return label;
        // Last.fm URLs keep parentheses and asterisks from names, which would end the link early or start italics
        return "[" + label + "](" + url.replace("(", "%28").replace(")", "%29").replace("*", "%2A") + ")";
    }

    private static String escape(String text) {
        if (text == null) return "";
        String trimmed = text.length() > NAME_LIMIT ? text.substring(0, NAME_LIMIT - 1) + "…" : text;
        // Escape every markdown character, not just complete pairs: "B*Witched" inside **bold** breaks it otherwise
        return MarkdownSanitizer.escape(trimmed, true);
    }

    /** Escapes markdown in lyrics, including line starts Discord would turn into headings, quotes or lists. */
    private static String escapeLyrics(String lyrics) {
        StringBuilder escaped = new StringBuilder();
        for (String line : MarkdownSanitizer.escape(lyrics, true).split("\n", -1)) {
            if (!line.isEmpty() && "#>-".indexOf(line.charAt(0)) >= 0) {
                escaped.append('\\');
            }
            escaped.append(line).append('\n');
        }
        return escaped.toString().stripTrailing();
    }

    /** "https://www.last.fm/music/Artist/_/Track" to "https://www.last.fm/music/Artist". */
    private static String artistUrlFromTrackUrl(String trackUrl) {
        if (trackUrl == null) return null;
        int split = trackUrl.indexOf("/_/");
        return split > 0 ? trackUrl.substring(0, split) : null;
    }

    private static String plays(long count) {
        return number(count) + (count == 1 ? " play" : " plays");
    }

    static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static long parseCount(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
