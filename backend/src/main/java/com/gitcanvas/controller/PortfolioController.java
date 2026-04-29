package com.gitcanvas.controller;

import com.gitcanvas.service.PortfolioService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /**
     * Generates a portfolio for the given username.
     * Expects the user to have been analyzed first (via /api/analyze/{username}).
     *
     * Returns the preview URL that the frontend iframe should load.
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generatePortfolio(@RequestBody Map<String, String> body) {
        String username = body.get("username");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Username is required"));
        }

        username = username.trim().toLowerCase();

        try {
            portfolioService.generate(username);

            String previewUrl = "/api/portfolio/preview/" + username + "/index.html";

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "previewUrl", previewUrl,
                    "username", username
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to generate portfolio: " + e.getMessage()));
        }
    }

    /**
     * Serves generated portfolio files for iframe preview.
     *
     * The iframe loads /api/portfolio/preview/{username}/index.html
     * and the HTML references style.css and script.js with relative paths.
     * The browser resolves those relative paths against the same base URL,
     * so it requests:
     *   /api/portfolio/preview/{username}/style.css
     *   /api/portfolio/preview/{username}/script.js
     *
     * We set the correct Content-Type header for each file type so the
     * browser interprets them correctly.
     */
    @GetMapping("/preview/{username}/{filename}")
    public ResponseEntity<?> servePreviewFile(
            @PathVariable String username,
            @PathVariable String filename) {

        String content = portfolioService.getFile(username, filename);

        if (content == null) {
            return ResponseEntity.notFound().build();
        }

        // Set the correct content type based on file extension
        MediaType contentType = switch (filename) {
            case "index.html" -> MediaType.TEXT_HTML;
            case "style.css" -> MediaType.valueOf("text/css");
            case "script.js" -> MediaType.valueOf("application/javascript");
            default -> MediaType.TEXT_PLAIN;
        };

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(content);
    }
}
