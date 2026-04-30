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

    /**
     * Returns all three portfolio files as JSON for the code editor.
     *
     * The editor needs the raw file contents to populate Monaco models.
     * We return them as a single JSON object rather than three separate
     * requests to minimize latency when opening the editor.
     */
    @GetMapping("/files/{username}")
    public ResponseEntity<?> getPortfolioFiles(@PathVariable String username) {
        var files = portfolioService.getPortfolioFiles(username);

        if (files == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "html", files.getHtml(),
                "css", files.getCss(),
                "js", files.getJs()
        ));
    }

    /**
     * Updates the stored portfolio files with user-edited content.
     *
     * Called when the user clicks "Finalize Changes" in the code editor.
     * Expects a JSON body with "html", "css", and "js" keys.
     *
     * After this call, the preview endpoint will serve the updated files,
     * so the frontend just needs to reload the iframe.
     */
    @PutMapping("/update/{username}")
    public ResponseEntity<?> updatePortfolioFiles(
            @PathVariable String username,
            @RequestBody Map<String, String> body) {

        String html = body.get("html");
        String css = body.get("css");
        String js = body.get("js");

        if (html == null || css == null || js == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "All three files (html, css, js) are required"));
        }

        try {
            var updatedFiles = new com.gitcanvas.model.PortfolioFiles(html, css, js);
            portfolioService.updatePortfolioFiles(username, updatedFiles);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Portfolio updated successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(
                    Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to update portfolio: " + e.getMessage()));
        }
    }
}
