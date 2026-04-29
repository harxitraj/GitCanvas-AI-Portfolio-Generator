package com.gitcanvas.service;

import com.gitcanvas.model.AnalysisResult;
import com.gitcanvas.model.GitHubProfile;
import com.gitcanvas.model.RepoInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);
    private static final String GITHUB_API = "https://api.github.com";

    private final RestTemplate restTemplate;

    @Value("${github.api.token}")
    private String githubToken;

    public GitHubService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Main entry point — fetches everything from GitHub and runs
     * our analysis pipeline: scoring, language aggregation, commit activity.
     */
    public AnalysisResult analyzeUser(String username) {
        log.info("Starting analysis for user: {}", username);

        // 1. Fetch profile
        GitHubProfile profile = fetchProfile(username);
        log.info("Fetched profile: {} ({})", profile.getName(), profile.getLogin());

        // 2. Fetch all repos with pagination
        List<RepoInfo> repos = fetchAllRepos(username);
        log.info("Fetched {} repositories (excluding forks)", repos.size());

        // 3. Get recent commit activity from Events API
        Map<String, Integer> monthlyCommits = fetchCommitActivity(username);
        log.info("Aggregated commit activity: {} months of data", monthlyCommits.size());

        // 4. Aggregate language usage across all repos
        Map<String, Integer> languageStats = aggregateLanguages(repos);

        // 5. Score every repo using our ranking algorithm
        scoreRepos(repos);

        // 6. Pick the top 5 by score
        List<RepoInfo> topRepos = repos.stream()
                .sorted(Comparator.comparingInt(RepoInfo::getScore).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // 7. Sum up totals
        int totalStars = repos.stream().mapToInt(RepoInfo::getStars).sum();
        int totalForks = repos.stream().mapToInt(RepoInfo::getForks).sum();

        // 8. Figure out their primary language
        String primaryLanguage = languageStats.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");

        // Build the result
        AnalysisResult result = new AnalysisResult();
        result.setProfile(profile);
        result.setAllRepos(repos);
        result.setTopRepos(topRepos);
        result.setLanguageStats(languageStats);
        result.setMonthlyCommits(monthlyCommits);
        result.setTotalStars(totalStars);
        result.setTotalForks(totalForks);
        result.setPrimaryLanguage(primaryLanguage);

        log.info("Analysis complete for {}. Primary language: {}, Top repos: {}",
                username, primaryLanguage, topRepos.size());
        return result;
    }

    // ---------------------------------------------------------------
    //  GitHub API calls
    // ---------------------------------------------------------------

    private GitHubProfile fetchProfile(String username) {
        String url = GITHUB_API + "/users/" + username;
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());

        ResponseEntity<GitHubProfile> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, GitHubProfile.class);

        if (response.getBody() == null) {
            throw new RuntimeException("Empty response from GitHub for user: " + username);
        }
        return response.getBody();
    }

    /**
     * Fetches ALL public repos with pagination.
     * GitHub returns max 100 per page. We loop until we get a page
     * with fewer than 100 results, which means we've hit the last page.
     *
     * We filter out forks because they aren't the user's original work
     * and would skew our language/scoring analysis.
     */
    private List<RepoInfo> fetchAllRepos(String username) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        List<RepoInfo> allRepos = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = String.format(
                    "%s/users/%s/repos?per_page=100&page=%d&sort=updated&type=owner",
                    GITHUB_API, username, page);

            ResponseEntity<RepoInfo[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, RepoInfo[].class);

            RepoInfo[] batch = response.getBody();
            if (batch == null || batch.length == 0) break;

            for (RepoInfo repo : batch) {
                if (!repo.isFork()) {
                    allRepos.add(repo);
                }
            }

            // If we got fewer than 100, this was the last page
            if (batch.length < 100) break;
            page++;
        }

        return allRepos;
    }

    /**
     * Uses the Events API to get recent commit activity.
     *
     * Why Events API instead of per-repo stats?
     * - Per-repo stats (/repos/{owner}/{repo}/stats/participation) costs 1 API call
     *   per repo. A user with 80 repos = 80 calls. That's expensive.
     * - The Events API gives us the last ~90 days of activity in 1-3 paginated calls.
     * - Tradeoff: less historical depth, but much cheaper on rate limit.
     *
     * We filter for PushEvent types and aggregate commit counts by month.
     */
    private Map<String, Integer> fetchCommitActivity(String username) {
        HttpEntity<String> entity = new HttpEntity<>(createHeaders());
        Map<String, Integer> monthlyCommits = new TreeMap<>();
        int page = 1;

        while (page <= 3) { // Events API returns max 10 pages, but 3 is plenty
            String url = String.format(
                    "%s/users/%s/events/public?per_page=100&page=%d",
                    GITHUB_API, username, page);

            try {
                ResponseEntity<List> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, List.class);

                List<?> events = response.getBody();
                if (events == null || events.isEmpty()) break;

                for (Object event : events) {
                    if (event instanceof Map<?, ?> eventMap) {
                        String type = (String) eventMap.get("type");
                        if ("PushEvent".equals(type)) {
                            String createdAt = (String) eventMap.get("created_at");
                            if (createdAt != null && createdAt.length() >= 7) {
                                // Extract "2026-04" from "2026-04-28T14:30:00Z"
                                String month = createdAt.substring(0, 7);
                                
                                // Count actual commits in this push event
                                int commitCount = 1;
                                Object payload = eventMap.get("payload");
                                if (payload instanceof Map<?, ?> payloadMap) {
                                    Object size = payloadMap.get("size");
                                    if (size instanceof Integer s) {
                                        commitCount = s;
                                    }
                                }
                                monthlyCommits.merge(month, commitCount, Integer::sum);
                            }
                        }
                    }
                }

                if (events.size() < 100) break;
                page++;
            } catch (Exception e) {
                log.warn("Failed to fetch events page {} for {}: {}", page, username, e.getMessage());
                break;
            }
        }

        return monthlyCommits;
    }

    // ---------------------------------------------------------------
    //  Analysis logic
    // ---------------------------------------------------------------

    /**
     * Counts how many repos use each language.
     * Returns a LinkedHashMap sorted by count descending, so the most-used
     * language comes first.
     */
    private Map<String, Integer> aggregateLanguages(List<RepoInfo> repos) {
        Map<String, Integer> stats = new HashMap<>();

        for (RepoInfo repo : repos) {
            String lang = repo.getLanguage();
            if (lang != null && !lang.isEmpty()) {
                stats.merge(lang, 1, Integer::sum);
            }
        }

        // Sort by count descending
        return stats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    /**
     * Scoring algorithm for repos:
     *   score = (stars × 3) + (forks × 2) + recency_bonus
     *
     * Recency bonus rewards actively maintained projects:
     *   - Updated within 30 days:  +15
     *   - Updated within 90 days:  +10
     *   - Updated within 180 days: +5
     *   - Older: +0
     */
    private void scoreRepos(List<RepoInfo> repos) {
        Instant now = Instant.now();

        for (RepoInfo repo : repos) {
            int score = (repo.getStars() * 3) + (repo.getForks() * 2);

            // Recency bonus
            try {
                Instant updated = Instant.parse(repo.getUpdatedAt());
                long daysAgo = Duration.between(updated, now).toDays();

                if (daysAgo <= 30) score += 15;
                else if (daysAgo <= 90) score += 10;
                else if (daysAgo <= 180) score += 5;
            } catch (Exception e) {
                // If we can't parse the date, just skip the recency bonus.
                // This shouldn't happen with GitHub's ISO 8601 format,
                // but defensive coding never hurts.
            }

            repo.setScore(score);
        }
    }

    /**
     * Creates HTTP headers for GitHub API requests.
     * Includes the PAT for authentication if available.
     * Without the token we get 60 req/hour; with it, 5000.
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");

        if (githubToken != null
                && !githubToken.isBlank()
                && !githubToken.startsWith("YOUR_")) {
            headers.set("Authorization", "Bearer " + githubToken);
        }

        return headers;
    }
}
