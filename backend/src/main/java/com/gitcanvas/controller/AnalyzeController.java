package com.gitcanvas.controller;

import com.gitcanvas.model.AnalysisResult;
import com.gitcanvas.service.PortfolioService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final PortfolioService portfolioService;

    public AnalyzeController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /**
     * Fetches and analyzes a GitHub user's public profile and repositories.
     *
     * Returns a structured analysis with:
     * - Profile info (name, bio, avatar, stats)
     * - All public repos (non-fork)
     * - Top 5 repos ranked by our scoring algorithm
     * - Language distribution
     * - Recent commit activity
     */
    @GetMapping("/analyze/{username}")
    public ResponseEntity<?> analyzeUser(@PathVariable String username) {
        // Validate username format
        if (!username.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$")) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Invalid GitHub username format"));
        }

        try {
            AnalysisResult result = portfolioService.analyze(username);
            return ResponseEntity.ok(result);

        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.status(404).body(
                    Map.of("error", "GitHub user '" + username + "' not found"));

        } catch (HttpClientErrorException.Forbidden e) {
            // This happens when GitHub rate limit is exceeded
            return ResponseEntity.status(429).body(
                    Map.of("error", "GitHub API rate limit exceeded. Please wait a few minutes and try again."));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to analyze user: " + e.getMessage()));
        }
    }
}
