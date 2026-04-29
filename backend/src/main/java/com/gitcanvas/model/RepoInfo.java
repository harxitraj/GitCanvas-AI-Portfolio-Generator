package com.gitcanvas.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps to a single repository from the GitHub Repos API.
 * The 'score' field is NOT from GitHub — we calculate it
 * using our scoring algorithm (stars*3 + forks*2 + recency_bonus).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepoInfo {

    private String name;
    private String description;

    @JsonProperty("stargazers_count")
    private int stars;

    @JsonProperty("forks_count")
    private int forks;

    private String language;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("html_url")
    private String htmlUrl;

    private boolean fork;

    // Calculated by our scoring algorithm — not from GitHub
    private int score;

    // --- Getters and Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public int getForks() { return forks; }
    public void setForks(int forks) { this.forks = forks; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }

    public boolean isFork() { return fork; }
    public void setFork(boolean fork) { this.fork = fork; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
}
