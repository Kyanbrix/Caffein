package com.github.kyanbrix.component.slashcommand.responses;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
public class LastFmTopTracksResponse {

    @JsonProperty("toptracks")
    private TopTracksContainer topTracks;

    public TopTracksContainer getTopTracks() { return topTracks; }
    public void setTopTracks(TopTracksContainer topTracks) { this.topTracks = topTracks; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TopTracksContainer {
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
        private String duration;
        private String playcount;
        private Streamable streamable;
        private List<Image> image;
        private Artist artist;

        @JsonProperty("@attr")
        private TrackAttributes attributes;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getMbid() { return mbid; }
        public void setMbid(String mbid) { this.mbid = mbid; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public String getPlaycount() { return playcount; }
        public void setPlaycount(String playcount) { this.playcount = playcount; }

        public Streamable getStreamable() { return streamable; }
        public void setStreamable(Streamable streamable) { this.streamable = streamable; }

        public List<Image> getImage() { return image; }
        public void setImage(List<Image> image) { this.image = image; }

        public Artist getArtist() { return artist; }
        public void setArtist(Artist artist) { this.artist = artist; }

        public TrackAttributes getAttributes() { return attributes; }
        public void setAttributes(TrackAttributes attributes) { this.attributes = attributes; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Artist {
        private String name;
        private String url;
        private String mbid;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getMbid() { return mbid; }
        public void setMbid(String mbid) { this.mbid = mbid; }
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
    public static class Streamable {
        private String fulltrack;

        @JsonProperty("#text")
        private String text;

        public String getFulltrack() { return fulltrack; }
        public void setFulltrack(String fulltrack) { this.fulltrack = fulltrack; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TrackAttributes {
        private String rank;

        public String getRank() { return rank; }
        public void setRank(String rank) { this.rank = rank; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageAttributes {
        private String perPage;
        private String totalPages;
        private String page;
        private String total;
        private String user;

        public String getPerPage() { return perPage; }
        public void setPerPage(String perPage) { this.perPage = perPage; }

        public String getTotalPages() { return totalPages; }
        public void setTotalPages(String totalPages) { this.totalPages = totalPages; }

        public String getPage() { return page; }
        public void setPage(String page) { this.page = page; }

        public String getTotal() { return total; }
        public void setTotal(String total) { this.total = total; }

        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
    }

}
