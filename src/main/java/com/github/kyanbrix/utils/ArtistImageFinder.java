package com.github.kyanbrix.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Image;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which artist a Last.fm chart entry really is, and finds their photo
 * and their name as they write it. Last.fm stopped serving artist photos in 2019,
 * and it treats names case-insensitively ("nicole", the Filipino singer, and
 * "Nicole", the German one, are one Last.fm artist), so its name and MusicBrainz ID
 * can point at the wrong person. Identity is settled like this:
 * <ol>
 *     <li>Last.fm's MusicBrainz ID leads to a Spotify artist ID, through the
 *     artist's Wikidata entry or else MusicBrainz's own links.</li>
 *     <li>The user's most played song by the artist checks it: if that Spotify
 *     artist's top songs don't include it, MusicBrainz is asked who's credited on
 *     the song, and that artist is used instead.</li>
 *     <li>The photo and exact name come from Spotify's public embed page for the
 *     artist (no API access or Premium needed), else the Wikidata entry's photo.</li>
 *     <li>Only with no ID at all, a Spotify Web API search that must match the name
 *     exactly, casing included. The Web API refuses apps whose owner has no
 *     Premium, so this step usually finds nothing and the artist gets an initials tile.</li>
 * </ol>
 * Everything here blocks.
 */
public final class ArtistImageFinder {

    private static final Logger log = LoggerFactory.getLogger(ArtistImageFinder.class);

    /** Wikimedia asks API clients to identify themselves with a way to reach the operator. */
    private static final String USER_AGENT = "CaffeinBot/1.0 (https://github.com/Kyanbrix/Caffein)";
    private static final String WIKIDATA_API = "https://www.wikidata.org/w/api.php";
    /** One of Wikimedia's standard thumbnail widths (others get rounded up to the next one); tiles are 300px. */
    private static final int PHOTO_WIDTH = 500;

    private static final Pattern MBID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern QID = Pattern.compile("Q\\d+");
    private static final Pattern SPOTIFY_ID = Pattern.compile("[0-9A-Za-z]{22}");
    private static final Pattern NEXT_DATA = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Version and feature notes that differ between services: "(Stripped)", "[Live]", "- Remastered 2011", "feat. X". */
    private static final Pattern TITLE_EXTRAS = Pattern.compile("\\s*[(\\[].*?[)\\]]|\\s+-\\s+.*$|\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Who a chart entry turned out to be.
     *
     * @param name     the artist's name as they write it (Spotify's or MusicBrainz's casing), else Last.fm's
     * @param photoUrl null when no source had a photo
     */
    public record ResolvedArtist(String name, String photoUrl) {}

    private enum Source { SPOTIFY, WIKIDATA, NONE }

    private record Found(ResolvedArtist artist, Source source) {}

    /**
     * Spotify results are kept for days. Others only for hours, since they're often
     * there because a lookup failed once, and a retry may find the Spotify photo.
     * Results without a photo are kept too, so MusicBrainz's one-request-a-second
     * limit isn't spent on the same artist every time.
     */
    private static final Cache<String, Found> CACHE = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(new Expiry<String, Found>() {
                @Override
                public long expireAfterCreate(String key, Found found, long currentTime) {
                    Duration lifetime = found.source() == Source.SPOTIFY ? Duration.ofDays(5) : Duration.ofHours(6);
                    return lifetime.toNanos();
                }

                @Override
                public long expireAfterUpdate(String key, Found found, long currentTime, long currentDuration) {
                    return expireAfterCreate(key, found, currentTime);
                }

                @Override
                public long expireAfterRead(String key, Found found, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    private ArtistImageFinder() {}

    /**
     * Works out who a Last.fm chart entry is; never null.
     *
     * @param lastFmName the name Last.fm gives (its casing may belong to a different artist)
     * @param mbid       Last.fm's MusicBrainz ID for it, or null/empty
     * @param userSong   the user's most played song by this artist, or null if unknown;
     *                   it's what tells apart artists Last.fm has merged
     */
    public static ResolvedArtist resolve(String lastFmName, String mbid, String userSong) {
        String validMbid = mbid != null && MBID.matcher(mbid).matches() ? mbid : null;
        // Case-sensitive on purpose: differently cased names can be different artists
        String key = lastFmName + "\u0000" + Objects.toString(validMbid, "") + "\u0000" + Objects.toString(userSong, "");

        Found cached = CACHE.getIfPresent(key);
        if (cached != null) {
            return cached.artist();
        }

        Found found = lookUp(lastFmName, validMbid, userSong);
        log.debug("Artist '{}' (mbid {}, song '{}') resolved to '{}' from {}: {}",
                lastFmName, mbid, userSong, found.artist().name(), found.source(), found.artist().photoUrl());
        CACHE.put(key, found);
        return found.artist();
    }

    /** Downloads and decodes an image with timeouts (unlike ImageIO.read(URL)); null on any failure. */
    public static BufferedImage download(String url) {
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Image {} returned HTTP {}", url, response.code());
                return null;
            }
            // In memory: ImageIO.read(InputStream) would buffer through a temp file on disk
            return ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(response.body().bytes())));
        } catch (Exception e) {
            log.warn("Failed to download image {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Everything known about one MusicBrainz artist; any part may be null. */
    private record Identity(String mbid, String creditedName, String spotifyId, EmbedArtist spotify, String wikidataPhoto) {}

    private static Found lookUp(String lastFmName, String mbid, String userSong) {
        Identity identity = mbid == null ? null : identify(mbid, null);

        // Check Last.fm's pick against the song the user actually plays
        if (userSong != null && (identity == null || identity.spotify() == null || !identity.spotify().hasSong(userSong))) {
            MusicBrainzClient.CreditedArtist credited = MusicBrainzClient.creditedArtist(baseTitle(userSong), lastFmName);
            if (credited != null) {
                if (identity == null || !credited.mbid().equals(identity.mbid())) {
                    log.debug("'{}' by '{}' is credited to {} ({}), not Last.fm's {}",
                            userSong, lastFmName, credited.name(), credited.mbid(), mbid);
                    identity = identify(credited.mbid(), credited.name());
                } else if (identity.creditedName() == null) {
                    identity = new Identity(identity.mbid(), credited.name(), identity.spotifyId(), identity.spotify(), identity.wikidataPhoto());
                }
            }
        }

        if (identity != null) {
            String name = identity.spotify() != null ? identity.spotify().name()
                    : identity.creditedName() != null ? identity.creditedName() : lastFmName;
            if (identity.spotify() != null && identity.spotify().photoUrl() != null) {
                return new Found(new ResolvedArtist(name, identity.spotify().photoUrl()), Source.SPOTIFY);
            }
            if (identity.wikidataPhoto() != null) {
                return new Found(new ResolvedArtist(name, commonsUrl(identity.wikidataPhoto())), Source.WIKIDATA);
            }
            // A known artist without a photo anywhere: a name search could only find someone else
            if (identity.spotifyId() != null) {
                return new Found(new ResolvedArtist(name, null), Source.NONE);
            }
        }

        // No ID to go by: an exact-name Spotify search is all that's left
        String photo = spotifyByName(lastFmName);
        return new Found(new ResolvedArtist(lastFmName, photo), photo != null ? Source.SPOTIFY : Source.NONE);
    }

    /** Looks a MusicBrainz artist up on Wikidata, then MusicBrainz's own links for the Spotify ID, then Spotify. */
    private static Identity identify(String mbid, String creditedName) {
        WikidataArtist wikidata = wikidata(mbid);
        String spotifyId = wikidata != null ? wikidata.spotifyId() : null;
        if (spotifyId == null) {
            // Smaller artists often have no Wikidata entry, but MusicBrainz links their Spotify page
            spotifyId = MusicBrainzClient.spotifyId(mbid);
        }
        EmbedArtist spotify = spotifyId == null ? null : spotifyArtist(spotifyId);
        return new Identity(mbid, creditedName, spotifyId, spotify, wikidata != null ? wikidata.photoFile() : null);
    }

    /**
     * A song title reduced to what survives between services: "Panaginip (Stripped)",
     * "Panaginip - Remastered 2011" and "panaginip" all become "panaginip".
     */
    static String baseTitle(String title) {
        String base = TITLE_EXTRAS.matcher(Normalizer.normalize(title, Normalizer.Form.NFC)).replaceAll("");
        base = NOT_LETTER_OR_DIGIT.matcher(base.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        // A title that's all extras, like "(Intro)", keeps its full text instead of vanishing
        return base.isEmpty() ? NOT_LETTER_OR_DIGIT.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").trim() : base;
    }

    // ---- Spotify ----

    /**
     * A Spotify artist as their public embed page shows them.
     *
     * @param songs the titles of their top songs on Spotify
     */
    record EmbedArtist(String name, String photoUrl, List<String> songs) {
        boolean hasSong(String title) {
            String wanted = baseTitle(title);
            return songs.stream().anyMatch(song -> baseTitle(song).equals(wanted));
        }
    }

    /**
     * This exact Spotify artist, from the data in Spotify's public embed player page
     * (the one link previews use). Unlike the Web API it needs no token, so it works
     * without Premium. Null if the page doesn't have them.
     */
    private static EmbedArtist spotifyArtist(String spotifyId) {
        HttpUrl url = HttpUrl.get("https://open.spotify.com/embed/artist/" + spotifyId);
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Spotify embed page for artist {} returned HTTP {}", spotifyId, response.code());
                return null;
            }

            EmbedArtist artist = parseEmbed(spotifyId, response.body().string());
            if (artist == null) {
                log.warn("Spotify embed page for artist {} had no artist data; its layout may have changed", spotifyId);
            }
            return artist;
        } catch (Exception e) {
            log.warn("Spotify embed lookup for artist {} failed: {}", spotifyId, e.getMessage());
            return null;
        }
    }

    /**
     * The artist in an embed page's player data, with their largest image; null if
     * the page has no data or describes a different artist (the page answers 200
     * even for an unknown ID).
     */
    static EmbedArtist parseEmbed(String spotifyId, String html) throws IOException {
        Matcher data = NEXT_DATA.matcher(html);
        if (!data.find()) {
            return null;
        }

        JsonNode entity = mapper.readTree(data.group(1)).path("props").path("pageProps").path("state").path("data").path("entity");
        String name = entity.path("name").asText("");
        if (!spotifyId.equals(entity.path("id").asText()) || name.isEmpty()) {
            return null;
        }

        String largest = null;
        long largestArea = 0;
        for (JsonNode image : entity.path("visualIdentity").path("image")) {
            long area = image.path("maxWidth").asLong() * image.path("maxHeight").asLong();
            String imageUrl = image.path("url").asText("");
            if (!imageUrl.isEmpty() && area > largestArea) {
                largest = imageUrl;
                largestArea = area;
            }
        }

        List<String> songs = new ArrayList<>();
        for (JsonNode track : entity.path("trackList")) {
            String title = track.path("title").asText("");
            if (!title.isEmpty()) songs.add(title);
        }
        return new EmbedArtist(name, largest, songs);
    }

    private static String spotifyByName(String name) {
        String accessToken = SpotifyAccessToken.get();
        if (accessToken == null) return null;

        try {
            Artist[] results = new SpotifyApi.Builder().setAccessToken(accessToken).build()
                    .searchArtists(name).limit(10).build().execute().getItems();

            List<Candidate> candidates = Arrays.stream(results)
                    .map(result -> new Candidate(result.getName(),
                            result.getPopularity() == null ? 0 : result.getPopularity(),
                            result.getFollowers() == null || result.getFollowers().getTotal() == null ? 0 : result.getFollowers().getTotal(),
                            largestImage(result.getImages())))
                    .toList();
            return pickByName(name, candidates);
        } catch (ForbiddenException | TooManyRequestsException e) {
            SpotifyAccessToken.backOff(e.getMessage());
        } catch (Exception e) {
            log.warn("Spotify artist search for '{}' failed: {}", name, e.getMessage());
        }
        return null;
    }

    /** One Spotify search result, reduced to what {@link #pickByName} weighs. */
    record Candidate(String name, int popularity, int followers, String imageUrl) {}

    /**
     * The photo of the search result whose name matches exactly, casing included
     * ("Nicole" and "nicole" are different artists), with the most popular match
     * winning when several artists share the exact name (someone's top artist is far
     * more likely the well-known one). Null when no name matches: a near miss is a
     * different artist more often than not.
     */
    static String pickByName(String name, List<Candidate> candidates) {
        String wanted = normalize(name);
        if (wanted.isEmpty()) return null;

        return candidates.stream()
                .filter(candidate -> candidate.imageUrl() != null && normalize(candidate.name()).equals(wanted))
                .max(Comparator.comparingInt(Candidate::popularity).thenComparingInt(Candidate::followers))
                .map(Candidate::imageUrl)
                .orElse(null);
    }

    /**
     * A name as written, only with its Unicode composed the same way ("é" as one
     * character or as "e" plus an accent) and its spacing tidied. Casing, accents
     * and punctuation are kept, since they're what tell artists like "Nicole" and
     * "nicole" apart.
     */
    static String normalize(String name) {
        return WHITESPACE.matcher(Normalizer.normalize(name, Normalizer.Form.NFC)).replaceAll(" ").trim();
    }

    /** Spotify lists images largest first. */
    private static String largestImage(Image[] images) {
        if (images == null) return null;
        for (Image image : images) {
            if (image.getUrl() != null && !image.getUrl().isEmpty()) {
                return image.getUrl();
            }
        }
        return null;
    }

    // ---- Wikidata ----

    /** What the artist's Wikidata entry says; either part may be null. */
    private record WikidataArtist(String spotifyId, String photoFile) {}

    /** The Wikidata entry with this MusicBrainz artist ID (property P434), or null if there's none. */
    private static WikidataArtist wikidata(String mbid) {
        try {
            JsonNode search = getJson(HttpUrl.get(WIKIDATA_API).newBuilder()
                    .addQueryParameter("action", "query")
                    .addQueryParameter("list", "search")
                    .addQueryParameter("srsearch", "haswbstatement:P434=" + mbid)
                    .addQueryParameter("srlimit", "1")
                    .addQueryParameter("format", "json")
                    .build());
            JsonNode hit = search.path("query").path("search").path(0);
            String qid = hit.path("title").asText("");
            if (!QID.matcher(qid).matches()) {
                return null;
            }

            JsonNode claims = getJson(HttpUrl.get(WIKIDATA_API).newBuilder()
                    .addQueryParameter("action", "wbgetentities")
                    .addQueryParameter("ids", qid)
                    .addQueryParameter("props", "claims")
                    .addQueryParameter("format", "json")
                    .build()).path("entities").path(qid).path("claims");

            String spotifyId = bestClaim(claims.path("P1902"));
            if (spotifyId != null && !SPOTIFY_ID.matcher(spotifyId).matches()) {
                spotifyId = null;
            }
            return new WikidataArtist(spotifyId, bestClaim(claims.path("P18")));
        } catch (Exception e) {
            log.warn("Wikidata lookup for MusicBrainz artist {} failed: {}", mbid, e.getMessage());
            return null;
        }
    }

    /** A claim's string value, taking a "preferred" one over "normal" ones and never a "deprecated" one. */
    private static String bestClaim(JsonNode claims) {
        String normal = null;
        for (JsonNode claim : claims) {
            String rank = claim.path("rank").asText();
            String value = claim.path("mainsnak").path("datavalue").path("value").asText(null);
            if (value == null || value.isBlank() || "deprecated".equals(rank)) continue;
            if ("preferred".equals(rank)) return value;
            if (normal == null) normal = value;
        }
        return normal;
    }

    /** A {@value #PHOTO_WIDTH}px wide rendition of a Commons file; the URL redirects to the thumbnail. */
    private static String commonsUrl(String file) {
        return HttpUrl.get("https://commons.wikimedia.org/wiki/Special:FilePath/").newBuilder()
                .addPathSegment(file.replace(' ', '_'))
                .addQueryParameter("width", String.valueOf(PHOTO_WIDTH))
                .build()
                .toString();
    }

    private static JsonNode getJson(HttpUrl url) throws IOException {
        Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return Objects.requireNonNull(mapper.readTree(response.body().string()));
        }
    }
}
