package com.gitcanvas.service;

import com.gitcanvas.model.AnalysisResult;
import com.gitcanvas.model.PortfolioFiles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates portfolio generation and stores results in memory.
 *
 * Why in-memory storage (ConcurrentHashMap) instead of a database?
 * - Each portfolio is ~25KB (HTML + CSS + JS combined). Even 1000 portfolios = ~25MB.
 * - This is a demo / SaaS MVP — we don't need persistence across server restarts.
 * - In production, you'd use Redis with a TTL so entries expire after a few hours.
 * - ConcurrentHashMap handles concurrent reads/writes safely without explicit locking.
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    private final GitHubService gitHubService;
    private final GeminiService geminiService;

    // username -> generated portfolio files
    private final ConcurrentHashMap<String, PortfolioFiles> portfolioStore = new ConcurrentHashMap<>();

    // username -> analysis result (cached so we don't re-fetch from GitHub)
    private final ConcurrentHashMap<String, AnalysisResult> analysisCache = new ConcurrentHashMap<>();

    public PortfolioService(GitHubService gitHubService, GeminiService geminiService) {
        this.gitHubService = gitHubService;
        this.geminiService = geminiService;
    }

    /**
     * Step 1: Analyze the GitHub user. Results are cached.
     */
    public AnalysisResult analyze(String username) {
        // Check cache first
        AnalysisResult cached = analysisCache.get(username);
        if (cached != null) {
            log.info("Returning cached analysis for: {}", username);
            return cached;
        }

        AnalysisResult result = gitHubService.analyzeUser(username);
        analysisCache.put(username, result);
        return result;
    }

    /**
     * Step 2: Generate the portfolio using Gemini.
     * Requires analysis to be done first.
     */
    public PortfolioFiles generate(String username) {
        AnalysisResult analysis = analysisCache.get(username);
        if (analysis == null) {
            // If analysis wasn't cached, do it now
            analysis = analyze(username);
        }

        PortfolioFiles files = geminiService.generatePortfolio(analysis);
        portfolioStore.put(username, files);

        log.info("Portfolio stored for: {}", username);
        return files;
    }

    /**
     * Retrieves a specific file from a generated portfolio.
     * Used by the preview endpoint to serve files to the iframe.
     */
    public String getFile(String username, String filename) {
        PortfolioFiles files = portfolioStore.get(username);
        if (files == null) return null;

        return switch (filename) {
            case "index.html" -> files.getHtml();
            case "style.css" -> files.getCss();
            case "script.js" -> files.getJs();
            default -> null;
        };
    }

    /**
     * Checks if a portfolio has been generated for this user.
     */
    public boolean hasPortfolio(String username) {
        return portfolioStore.containsKey(username);
    }
}
