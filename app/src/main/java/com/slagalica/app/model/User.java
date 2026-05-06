package com.slagalica.app.model;

public class User {

    private String id;
    private String username;
    private String email;
    private String region;
    private int tokens;
    private boolean emailVerified;

    public User() {}

    public User(String id, String username, String email, String region) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.region = region;
        this.tokens = 5;
        this.emailVerified = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getTokens() { return tokens; }
    public void setTokens(int tokens) { this.tokens = tokens; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
}
