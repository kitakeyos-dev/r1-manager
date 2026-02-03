package com.phicomm.r1manager.server.model;

public class Song {
    private String id;
    private String title;
    private String artist;
    private String thumbnail;
    private String streamUrl;
    private String duration; // String from API, but maybe we just need streamUrl mostly

    public Song() {
    }

    public Song(String id, String title, String artist, String thumbnail, String streamUrl) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.thumbnail = thumbnail;
        this.streamUrl = streamUrl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }
}
