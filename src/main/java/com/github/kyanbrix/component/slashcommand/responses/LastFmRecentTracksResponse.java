package com.github.kyanbrix.component.slashcommand.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class LastFmRecentTracksResponse {

    @JsonProperty("recenttracks")
    private RecentTracksContainer recentTracks;

    public RecentTracksContainer getRecentTracks() { return recentTracks; }
    public void setRecentTracks(RecentTracksContainer recentTracks) { this.recentTracks = recentTracks; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecentTracksContainer {
        @JsonProperty("track")
        private List<Track> tracks;

        @JsonProperty("@attr")
        private PageAttributes attributes;

        public List<Track> getTracks() { return tracks; }
        public void setTracks(List<Track> tracks) { this.tracks = tracks; }

        public PageAttributes getAttributes() { return attributes; }
        public void setAttributes(PageAttributes attributes) { this.attributes = attributes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Track {
        private String name;
        private String mbid;
        private String url;
        private String streamable;
        private Artist artist;
        private Album album;
        private List<Image> image;
        private DateInfo date;

        @JsonProperty("@attr")
        private TrackAttributes attributes;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getMbid() { return mbid; }
        public void setMbid(String mbid) { this.mbid = mbid; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getStreamable() { return streamable; }
        public void setStreamable(String streamable) { this.streamable = streamable; }

        public Artist getArtist() { return artist; }
        public void setArtist(Artist artist) { this.artist = artist; }

        public Album getAlbum() { return album; }
        public void setAlbum(Album album) { this.album = album; }

        public List<Image> getImage() { return image; }
        public void setImage(List<Image> image) { this.image = image; }

        public DateInfo getDate() { return date; }
        public void setDate(DateInfo date) { this.date = date; }

        public TrackAttributes getAttributes() { return attributes; }
        public void setAttributes(TrackAttributes attributes) { this.attributes = attributes; }

        // Helper method to easily check if this track is currently playing
        public boolean isNowPlaying() {
            return attributes != null && "true".equalsIgnoreCase(attributes.getNowPlaying());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Artist {
        private String mbid;

        @JsonProperty("#text")
        private String name;

        public String getMbid() { return mbid; }
        public void setMbid(String mbid) { this.mbid = mbid; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Album {
        private String mbid;

        @JsonProperty("#text")
        private String title;

        public String getMbid() { return mbid; }
        public void setMbid(String mbid) { this.mbid = mbid; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        private String size;

        @JsonProperty("#text")
        private String url;

        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackAttributes {
        @JsonProperty("nowplaying")
        private String nowPlaying;

        public String getNowPlaying() { return nowPlaying; }
        public void setNowPlaying(String nowPlaying) { this.nowPlaying = nowPlaying; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DateInfo {
        private String uts;

        @JsonProperty("#text")
        private String text;

        public String getUts() { return uts; }
        public void setUts(String uts) { this.uts = uts; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageAttributes {
        private String user;
        private String totalPages;
        private String page;
        private String perPage;
        private String total;

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }

        public String getTotalPages() { return totalPages; }
        public void setTotalPages(String totalPages) { this.totalPages = totalPages; }

        public String getPage() { return page; }
        public void setPage(String page) { this.page = page; }

        public String getPerPage() { return perPage; }
        public void setPerPage(String perPage) { this.perPage = perPage; }

        public String getTotal() { return total; }
        public void setTotal(String total) { this.total = total; }
    }
}
