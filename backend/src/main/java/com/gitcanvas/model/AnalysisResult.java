package com.gitcanvas.model;

import java.util.List;
import java.util.Map;

/**
 * The complete analysis output for a GitHub user.
 * This is what we send to Gemini as context for portfolio generation,
 * and what we return from the /api/analyze endpoint.
 */
public class AnalysisResult {

    private GitHubProfile profile;
    private List<RepoInfo> allRepos;
    private List<RepoInfo> topRepos;           // top 5 by score
    private Map<String, Integer> languageStats; // language -> repo count, sorted desc
    private Map<String, Integer> monthlyCommits; // "2026-04" -> commit count
    private int totalStars;
    private int totalForks;
    private String primaryLanguage;

    // --- Getters and Setters ---

    public GitHubProfile getProfile() { return profile; }
    public void setProfile(GitHubProfile profile) { this.profile = profile; }

    public List<RepoInfo> getAllRepos() { return allRepos; }
    public void setAllRepos(List<RepoInfo> allRepos) { this.allRepos = allRepos; }

    public List<RepoInfo> getTopRepos() { return topRepos; }
    public void setTopRepos(List<RepoInfo> topRepos) { this.topRepos = topRepos; }

    public Map<String, Integer> getLanguageStats() { return languageStats; }
    public void setLanguageStats(Map<String, Integer> languageStats) { this.languageStats = languageStats; }

    public Map<String, Integer> getMonthlyCommits() { return monthlyCommits; }
    public void setMonthlyCommits(Map<String, Integer> monthlyCommits) { this.monthlyCommits = monthlyCommits; }

    public int getTotalStars() { return totalStars; }
    public void setTotalStars(int totalStars) { this.totalStars = totalStars; }

    public int getTotalForks() { return totalForks; }
    public void setTotalForks(int totalForks) { this.totalForks = totalForks; }

    public String getPrimaryLanguage() { return primaryLanguage; }
    public void setPrimaryLanguage(String primaryLanguage) { this.primaryLanguage = primaryLanguage; }
}
