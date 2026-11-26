package com.github.kyanbrix.component.slashcommand;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.kyanbrix.Caffein;
import com.github.kyanbrix.api.lastfm.LastFmClient;
import com.github.kyanbrix.api.lastfm.LastFmException;
import com.github.kyanbrix.api.lastfm.LastFmPeriod;
import com.github.kyanbrix.api.lastfm.LastFmRecentTracksResponse;
import com.github.kyanbrix.api.lastfm.LastFmTopArtistsResponse;
import com.github.kyanbrix.api.lastfm.LastFmTopTracksResponse;
import com.github.kyanbrix.api.lastfm.LastFmTrackInfoResponse;
import com.github.kyanbrix.api.lyrics.LrcLibClient;
import com.github.kyanbrix.component.slashcommand.data.TrackMetadata;
import com.github.kyanbrix.component.slashcommand.responses.LrcLibResponse;
import com.github.kyanbrix.config.database.UserRepository;
import com.github.kyanbrix.utils.ArtistImageFinder;
import com.github.kyanbrix.utils.Constant;
import com.github.kyanbrix.utils.ImageColorExtractor;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * /fm: Last.fm stats for a linked account. {@code now}, {@code top tracks},
 * {@code top artists} and {@code lyrics} work for yourself or another linked
 * user; {@code link} and {@code unlink} manage your own link.
 */
public class LastFm implements ISlash {

    private static final Logger log = LoggerFactory.getLogger(LastFm.class);

    /**
     * First part of the pager buttons' custom IDs:
     * {@code fm:top:<action>:<ownerId>:<targetId>:<period>:<page>} for top tracks, and
     * {@code fm:lyr:<action>:<ownerId>:<lrclibId>:<page>}.
     */
    public static final String BUTTON_PREFIX = "fm";
    /**
     * Custom ID prefix of the top tracks period menu: {@code fm-period:tracks:<ownerId>:<targetId>}.
     * The oldest menus have no "tracks" part; top artists briefly had an "artists" menu, which no longer works.
     */
    public static final String PERIOD_SELECT_PREFIX = "fm-period:";
    static final String TRACKS_MENU_KEY = "tracks";

    static final int TRACKS_PER_PAGE = 5;
    static final int DEFAULT_ARTIST_COUNT = 9;
    static final int MAX_ARTIST_COUNT = 12;
    private static final LastFmPeriod DEFAULT_PERIOD = LastFmPeriod.MONTH;

    private static final String NOT_OWNER = "Only the person who ran this command can use these controls.";
    private static final String EXPIRED = "These controls have expired. Run the command again.";

    private static final UserRepository repository = new UserRepository();

    /** How many of the user's top songs {@link #topSongByArtist} reads; Last.fm's limit per request is 1000. */
    private static final int TOP_SONGS_LIMIT = 500;

    /** User and period to their top song by each artist; see {@link #topSongByArtist}. */
    private static final Cache<String, Map<String, String>> TOP_SONGS = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    /** Artist name (exact casing) to MusicBrainz ID, or "" when Last.fm has none. */
    private static final Cache<String, String> ARTIST_MBIDS = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterWrite(Duration.ofDays(7))
            .build();

    @Override
    public void execute(@NonNull SlashCommandInteraction event) {
        String group = event.getSubcommandGroup();
        String name = event.getSubcommandName();

        if ("top".equals(group)) {
            switch (name == null ? "" : name) {
                case "tracks" -> topTracks(event);
                case "artists" -> topArtists(event);
                default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
            }
            return;
        }

        switch (name == null ? "" : name) {
            case "now" -> nowPlaying(event);
            case "lyrics" -> lyrics(event);
            case "link" -> link(event);
            case "unlink" -> unlink(event);
            default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
        }
    }

    @Override
    public @NonNull CommandData getCommandData() {
        OptionData user = new OptionData(OptionType.USER, "user", "Whose stats to show (you by default)", false);

        OptionData period = new OptionData(OptionType.STRING, "period", "Which time range to rank (monthly by default)", false);
        for (LastFmPeriod value : LastFmPeriod.values()) {
            period.addChoice(value.label(), value.code());
        }

        OptionData artistCount = new OptionData(OptionType.INTEGER, "count",
                "How many artists to show (1-" + MAX_ARTIST_COUNT + ", " + DEFAULT_ARTIST_COUNT + " by default)", false)
                .setRequiredRange(1, MAX_ARTIST_COUNT);

        return Commands.slash("fm", "Your Last.fm stats")
                .addSubcommands(
                        new SubcommandData("now", "Show what's playing now, or the last played track").addOptions(user),
                        new SubcommandData("lyrics", "Show the lyrics of what's playing now").addOptions(user),
                        new SubcommandData("link", "Connect your Last.fm account"),
                        new SubcommandData("unlink", "Disconnect your Last.fm account"))
                .addSubcommandGroups(new SubcommandGroupData("top", "Most played music")
                        .addSubcommands(
                                new SubcommandData("tracks", "Your most played tracks").addOptions(period, user),
                                new SubcommandData("artists", "Your most played artists, as a picture grid").addOptions(period, artistCount, user)))
                .setIntegrationTypes(IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.PRIVATE_CHANNEL);
    }

    // ---- subcommands ----

    private void nowPlaying(SlashCommandInteraction event) {
        Target target = resolveTarget(event);
        if (target == null) return;

        event.deferReply().queue();
        runAsync(event.getHook(), target.lastFmName(), () -> {
            Latest latest = latestTrack(target.lastFmName());
            if (latest == null) {
                event.getHook().sendMessage(noScrobbles(target.lastFmName())).queue();
                return;
            }

            LastFmRecentTracksResponse.Track track = latest.track();
            LastFmTrackInfoResponse.Track info = trackInfo(track, target.lastFmName());

            String lastFmImage = track.getLargestImageUrl();
            if (lastFmImage == null && info != null && info.getAlbum() != null) {
                lastFmImage = info.getAlbum().getLargestImageUrl();
            }
            TrackMetadata metadata = TrackMetadataCache.getOrFetchMetadata(track.getArtist().getName(), track.getName(), lastFmImage);

            Container card = LastFmViews.nowPlaying(target.lastFmName(), track, info, metadata, latest.totalScrobbles());
            event.getHook().sendMessageComponents(card).useComponentsV2().queue();
        });
    }

    private void topTracks(SlashCommandInteraction event) {
        Target target = resolveTarget(event);
        if (target == null) return;

        LastFmPeriod period = periodOption(event);
        long ownerId = event.getUser().getIdLong();

        event.deferReply().queue();
        runAsync(event.getHook(), target.lastFmName(), () -> {
            Container page = topTracksPage(ownerId, target.discordId(), target.lastFmName(), period, 1);
            event.getHook().sendMessageComponents(page).useComponentsV2().queue();
        });
    }

    /** A one-off picture card: no pager or period menu, the options decide everything. */
    private void topArtists(SlashCommandInteraction event) {
        Target target = resolveTarget(event);
        if (target == null) return;

        LastFmPeriod period = periodOption(event);
        int count = Math.clamp(event.getOption("count", DEFAULT_ARTIST_COUNT, OptionMapping::getAsInt), 1, MAX_ARTIST_COUNT);

        event.deferReply().queue();
        runAsync(event.getHook(), target.lastFmName(), () -> {
            // The grid image is attached through its media gallery item
            Container card = topArtistsCard(target.lastFmName(), period, count);
            event.getHook().sendMessageComponents(card).useComponentsV2().queue();
        });
    }

    private static LastFmPeriod periodOption(SlashCommandInteraction event) {
        LastFmPeriod period = LastFmPeriod.fromCode(event.getOption("period", DEFAULT_PERIOD.code(), OptionMapping::getAsString));
        return period != null ? period : DEFAULT_PERIOD;
    }

    private void lyrics(SlashCommandInteraction event) {
        Target target = resolveTarget(event);
        if (target == null) return;

        long ownerId = event.getUser().getIdLong();

        event.deferReply().queue();
        runAsync(event.getHook(), target.lastFmName(), () -> {
            Latest latest = latestTrack(target.lastFmName());
            if (latest == null) {
                event.getHook().sendMessage(noScrobbles(target.lastFmName())).queue();
                return;
            }

            LastFmRecentTracksResponse.Track track = latest.track();
            String artist = track.getArtist().getName();

            Optional<LrcLibResponse> song = LrcLibClient.search(track.getName(), artist);
            if (song.isEmpty()) {
                event.getHook().sendMessage("Couldn't find lyrics for **" + MarkdownSanitizer.escape(track.getName(), true)
                        + "** by **" + MarkdownSanitizer.escape(artist, true) + "**.").queue();
                return;
            }

            // Keyed by LRCLIB's names so paging finds the same artwork in the cache
            TrackMetadata metadata = TrackMetadataCache.getOrFetchMetadata(song.get().artistName, song.get().trackName, track.getLargestImageUrl());
            event.getHook().sendMessageComponents(LastFmViews.lyrics(ownerId, song.get(), metadata, 1)).useComponentsV2().queue();
        });
    }

    private void link(SlashCommandInteraction event) {
        long userId = event.getUser().getIdLong();
        event.replyComponents(LastFmViews.linkPrompt(userId, repository.getUsernameFromLastFm(userId)))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }

    private void unlink(SlashCommandInteraction event) {
        try {
            boolean removed = repository.deleteLastFmLink(event.getUser().getIdLong());
            event.reply(removed
                            ? "Your Last.fm account is no longer linked. Use `/fm link` to connect one again."
                            : "You don't have a Last.fm account linked.")
                    .setEphemeral(true)
                    .queue();
        } catch (SQLException e) {
            log.error("Failed to unlink Last.fm for {}", event.getUser().getIdLong(), e);
            event.reply("Couldn't unlink your account right now. Try again in a bit.").setEphemeral(true).queue();
        }
    }

    // ---- buttons and menu, routed here from ButtonManager and StringSelectionComponent ----

    public static void handleButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        Long ownerId = parts.length > 3 ? parseLong(parts[3]) : null;
        if (ownerId == null) {
            event.reply(EXPIRED).setEphemeral(true).queue();
            return;
        }
        if (event.getUser().getIdLong() != ownerId) {
            event.reply(NOT_OWNER).setEphemeral(true).queue();
            return;
        }

        switch (parts[1]) {
            case "top" -> {
                Long targetId = parts.length == 7 ? parseLong(parts[4]) : null;
                LastFmPeriod period = parts.length == 7 ? LastFmPeriod.fromCode(parts[5]) : null;
                Long page = parts.length == 7 ? parseLong(parts[6]) : null;
                if (targetId == null || period == null || page == null || page < 1) {
                    event.reply(EXPIRED).setEphemeral(true).queue();
                    return;
                }

                event.deferEdit().queue();
                editTopTracks(event.getHook(), ownerId, targetId, period, page.intValue());
            }
            case "lyr" -> {
                Long songId = parts.length == 6 ? parseLong(parts[4]) : null;
                Long page = parts.length == 6 ? parseLong(parts[5]) : null;
                if (songId == null || page == null || page < 1) {
                    event.reply(EXPIRED).setEphemeral(true).queue();
                    return;
                }

                event.deferEdit().queue();
                InteractionHook hook = event.getHook();
                runAsync(hook, null, () -> {
                    Optional<LrcLibResponse> song = LrcLibClient.get(songId.intValue());
                    if (song.isEmpty()) {
                        hook.sendMessage("LRCLIB doesn't have these lyrics anymore.").setEphemeral(true).queue();
                        return;
                    }
                    TrackMetadata metadata = TrackMetadataCache.getOrFetchMetadata(song.get().artistName, song.get().trackName, null);
                    hook.editOriginalComponents(LastFmViews.lyrics(ownerId, song.get(), metadata, page.intValue())).useComponentsV2().queue();
                });
            }
            default -> event.reply(EXPIRED).setEphemeral(true).queue();
        }
    }

    /** The top tracks period menu: shows page 1 of the chosen period. */
    public static void handlePeriodSelect(StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().substring(PERIOD_SELECT_PREFIX.length()).split(":");
        if (parts.length == 2) {
            // One of the oldest menus, from before the "tracks" part
            parts = new String[]{TRACKS_MENU_KEY, parts[0], parts[1]};
        }
        boolean tracks = parts.length == 3 && TRACKS_MENU_KEY.equals(parts[0]);
        Long ownerId = tracks ? parseLong(parts[1]) : null;
        Long targetId = tracks ? parseLong(parts[2]) : null;
        LastFmPeriod period = event.getValues().isEmpty() ? null : LastFmPeriod.fromCode(event.getValues().getFirst());
        if (ownerId == null || targetId == null || period == null) {
            event.reply(EXPIRED).setEphemeral(true).queue();
            return;
        }
        if (event.getUser().getIdLong() != ownerId) {
            event.reply(NOT_OWNER).setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        editTopTracks(event.getHook(), ownerId, targetId, period, 1);
    }

    private static void editTopTracks(InteractionHook hook, long ownerId, long targetId, LastFmPeriod period, int page) {
        Caffein.getInstance().getExecutorService().submit(() -> {
            String lastFmName = repository.getUsernameFromLastFm(targetId);
            if (lastFmName == null) {
                hook.sendMessage("That Last.fm account isn't linked anymore.").setEphemeral(true).queue();
                return;
            }
            runTask(hook, lastFmName, () ->
                    hook.editOriginalComponents(topTracksPage(ownerId, targetId, lastFmName, period, page)).useComponentsV2().queue());
        });
    }

    // ---- data ----

    /**
     * One page of top tracks, with artwork fetched in parallel. A page past the
     * end (the user's history shrank since the buttons were made) shows the last page.
     */
    private static Container topTracksPage(long ownerId, long targetId, String lastFmName, LastFmPeriod period, int page) throws LastFmException {
        LastFmTopTracksResponse response = LastFmClient.topTracks(lastFmName, period, page, TRACKS_PER_PAGE);
        LastFmTopTracksResponse.TopTracksContainer topTracks = response.getTopTracks();

        List<LastFmTopTracksResponse.Track> tracks = topTracks == null || topTracks.getTracks() == null ? List.of() : topTracks.getTracks();
        // Last.fm sometimes sends more than the limit on the first page
        if (tracks.size() > TRACKS_PER_PAGE) {
            tracks = tracks.subList(0, TRACKS_PER_PAGE);
        }

        LastFmTopTracksResponse.PageAttributes attributes = topTracks == null ? null : topTracks.getAttributes();
        int totalPages = attributes == null ? 1 : (int) Math.max(1, parseLong(attributes.getTotalPages(), 1));
        long totalTracks = attributes == null ? tracks.size() : parseLong(attributes.getTotal(), tracks.size());

        if (tracks.isEmpty() && page > 1 && page > totalPages) {
            return topTracksPage(ownerId, targetId, lastFmName, period, totalPages);
        }

        List<CompletableFuture<TrackMetadata>> artwork = tracks.stream()
                .map(track -> CompletableFuture.supplyAsync(() -> TrackMetadataCache.getOrFetchMetadata(
                        track.getArtist().getName(), track.getName(), track.getLargestImageUrl()),
                        Caffein.getInstance().getExecutorService()))
                .toList();

        return LastFmViews.topTracks(ownerId, targetId, lastFmName, period, page, totalPages, totalTracks,
                tracks, artwork.stream().map(CompletableFuture::join).toList());
    }

    /**
     * The user's top {@code count} artists as a grid image. Last.fm has no artist
     * photos, so they're identified and downloaded in parallel; a missing one
     * becomes an initials tile.
     */
    private static Container topArtistsCard(String lastFmName, LastFmPeriod period, int count) throws Exception {
        LastFmTopArtistsResponse.TopArtistsContainer topArtists = LastFmClient.topArtists(lastFmName, period, 1, count).getTopArtists();

        List<LastFmTopArtistsResponse.Artist> artists = topArtists == null || topArtists.getArtists() == null ? List.of() : topArtists.getArtists();
        // Last.fm sometimes sends more than the limit
        if (artists.size() > count) {
            artists = artists.subList(0, count);
        }

        LastFmTopArtistsResponse.PageAttributes attributes = topArtists == null ? null : topArtists.getAttributes();
        long totalArtists = attributes == null ? artists.size() : parseLong(attributes.getTotal(), artists.size());

        if (artists.isEmpty()) {
            return LastFmViews.topArtists(lastFmName, period, totalArtists, List.of(), null, Constant.LAST_FM_RED);
        }

        Map<String, String> songs = topSongByArtist(lastFmName, period);

        List<CompletableFuture<TopArtistsGrid.Tile>> tileFutures = new ArrayList<>();
        for (int i = 0; i < artists.size(); i++) {
            LastFmTopArtistsResponse.Artist artist = artists.get(i);
            int rank = i + 1;
            tileFutures.add(CompletableFuture.supplyAsync(() -> {
                ArtistImageFinder.ResolvedArtist resolved = ArtistImageFinder.resolve(
                        artist.getName(), musicBrainzId(artist), songs.get(artist.getName()));
                BufferedImage photo = resolved.photoUrl() == null ? null : ArtistImageFinder.download(resolved.photoUrl());
                // The resolved name keeps the artist's own casing ("nicole"), which Last.fm may have changed
                return new TopArtistsGrid.Tile(rank, resolved.name(), artist.getPlayCount(), photo);
            }, Caffein.getInstance().getExecutorService()));
        }
        List<TopArtistsGrid.Tile> tiles = tileFutures.stream().map(CompletableFuture::join).toList();

        Color accent = ImageColorExtractor.getColor(tiles.getFirst().photo());
        FileUpload image = FileUpload.fromData(TopArtistsGrid.render(tiles), "top-artists-" + period.code() + ".jpg");

        return LastFmViews.topArtists(lastFmName, period, totalArtists, tiles, image, accent != null ? accent : Constant.LAST_FM_RED);
    }

    /**
     * The user's most played song by each artist in this period, keyed by Last.fm's
     * artist name. It's how {@link ArtistImageFinder} tells apart artists Last.fm
     * merges ("nicole" and "Nicole"). One request covers every page of the chart, and
     * it's kept a few minutes so paging doesn't repeat it. Empty if the request fails.
     */
    private static Map<String, String> topSongByArtist(String lastFmName, LastFmPeriod period) {
        String key = lastFmName + "\u0000" + period.code();
        Map<String, String> cached = TOP_SONGS.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        Map<String, String> songs = new HashMap<>();
        try {
            LastFmTopTracksResponse.TopTracksContainer top = LastFmClient.topTracks(lastFmName, period, 1, TOP_SONGS_LIMIT).getTopTracks();
            if (top != null && top.getTracks() != null) {
                // Most played first, so the first song per artist is their top one
                for (LastFmTopTracksResponse.Track track : top.getTracks()) {
                    if (track.getArtist() != null && track.getArtist().getName() != null && track.getName() != null) {
                        songs.putIfAbsent(track.getArtist().getName(), track.getName());
                    }
                }
            }
        } catch (LastFmException e) {
            log.warn("Couldn't load {}'s top songs to identify artists: {}", lastFmName, e.getMessage());
            return songs;
        }

        TOP_SONGS.put(key, songs);
        return songs;
    }

    /**
     * The artist's MusicBrainz ID, which lets {@link ArtistImageFinder} find their
     * photo by identity instead of by name. user.getTopArtists leaves it out for some
     * artists (Metallica, for one), so those are asked about separately; an ID never
     * changes, so answers are kept, including "Last.fm has none".
     */
    private static String musicBrainzId(LastFmTopArtistsResponse.Artist artist) {
        if (artist.getMbid() != null && !artist.getMbid().isBlank()) {
            return artist.getMbid();
        }

        // Case-sensitive: "Nicole" and "nicole" can be different artists
        String key = artist.getName();
        String cached = ARTIST_MBIDS.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        try {
            String mbid = LastFmClient.artistMbid(artist.getName());
            ARTIST_MBIDS.put(key, mbid);
            return mbid;
        } catch (LastFmException e) {
            log.debug("No MusicBrainz ID for '{}': {}", artist.getName(), e.getMessage());
            return null;
        }
    }

    /** The track playing now (or else the last scrobble) and the user's scrobble count. */
    private record Latest(LastFmRecentTracksResponse.Track track, long totalScrobbles) {}

    /** What's playing now, or else the last scrobble; null if the user has never scrobbled. */
    private static Latest latestTrack(String lastFmName) throws LastFmException {
        LastFmRecentTracksResponse.RecentTracksContainer recent = LastFmClient.recentTracks(lastFmName, 1).getRecentTracks();
        if (recent == null || recent.getTracks() == null || recent.getTracks().isEmpty()) {
            return null;
        }

        LastFmRecentTracksResponse.PageAttributes attributes = recent.getAttributes();
        long total = attributes == null ? 0 : parseLong(attributes.getTotal(), 0);
        return new Latest(recent.getTracks().getFirst(), total);
    }

    /** track.getInfo for the user's plays and loved status; null if it fails, since the card works without it. */
    private static LastFmTrackInfoResponse.Track trackInfo(LastFmRecentTracksResponse.Track track, String lastFmName) {
        try {
            return LastFmClient.trackInfo(track.getArtist().getName(), track.getName(), lastFmName).getTrack();
        } catch (LastFmException e) {
            log.debug("No track info for '{} - {}': {}", track.getArtist().getName(), track.getName(), e.getMessage());
            return null;
        }
    }

    // ---- helpers ----

    private record Target(long discordId, String lastFmName) {}

    /**
     * The user whose stats to show and their Last.fm name. When they haven't
     * linked an account this replies (ephemerally, before any defer) and returns null.
     */
    private static Target resolveTarget(SlashCommandInteraction event) {
        User user = event.getOption("user", event.getUser(), OptionMapping::getAsUser);
        boolean self = user.getIdLong() == event.getUser().getIdLong();

        if (user.isBot()) {
            event.reply("Bots don't scrobble.").setEphemeral(true).queue();
            return null;
        }

        String lastFmName = repository.getUsernameFromLastFm(user.getIdLong());
        if (lastFmName != null) {
            return new Target(user.getIdLong(), lastFmName);
        }

        if (self) {
            event.replyComponents(LastFmViews.linkPrompt(user.getIdLong(), null)).useComponentsV2().setEphemeral(true).queue();
        } else {
            event.reply("**" + MarkdownSanitizer.escape(user.getEffectiveName(), true) + "** hasn't linked a Last.fm account yet.")
                    .setEphemeral(true)
                    .queue();
        }
        return null;
    }

    private static String noScrobbles(String lastFmName) {
        return "**" + MarkdownSanitizer.escape(lastFmName, true) + "** hasn't scrobbled anything yet.";
    }

    @FunctionalInterface
    private interface Task {
        void run() throws Exception;
    }

    /** Runs Last.fm work off the event thread and always answers the hook, even when it fails. */
    private static void runAsync(InteractionHook hook, String lastFmName, Task task) {
        Caffein.getInstance().getExecutorService().submit(() -> runTask(hook, lastFmName, task));
    }

    private static void runTask(InteractionHook hook, String lastFmName, Task task) {
        try {
            task.run();
        } catch (LastFmException e) {
            log.warn("Last.fm call for {} failed (code {}): {}", lastFmName, e.getCode(), e.getMessage());
            hook.sendMessage(e.userMessage(lastFmName)).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("/fm failed for {}", lastFmName, e);
            hook.sendMessage("Something went wrong. Try again in a bit.").setEphemeral(true).queue();
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLong(String value, long fallback) {
        Long parsed = value == null ? null : parseLong(value);
        return parsed == null ? fallback : parsed;
    }
}
