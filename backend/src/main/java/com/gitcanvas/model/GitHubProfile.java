package com.gitcanvas.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps to the GitHub Users API response.
 * Only the fields we actually need are declared — Jackson
 * ignores everything else thanks to @JsonIgnoreProperties.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubProfile {

    private String login;
    private String name;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String bio;
    private String location;
    private String company;
    private String blog;

    @JsonProperty("twitter_username")
    private String twitterUsername;

    @JsonProperty("public_repos")
    private int publicRepos;

    private int followers;
    private int following;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("created_at")
    private String createdAt;

    // --- Getters and Setters ---

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getBlog() { return blog; }
    public void setBlog(String blog) { this.blog = blog; }

    public String getTwitterUsername() { return twitterUsername; }
    public void setTwitterUsername(String twitterUsername) { this.twitterUsername = twitterUsername; }

    public int getPublicRepos() { return publicRepos; }
    public void setPublicRepos(int publicRepos) { this.publicRepos = publicRepos; }

    public int getFollowers() { return followers; }
    public void setFollowers(int followers) { this.followers = followers; }

    public int getFollowing() { return following; }
    public void setFollowing(int following) { this.following = following; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
