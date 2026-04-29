package com.gitcanvas.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitcanvas.model.AnalysisResult;
import com.gitcanvas.model.PortfolioFiles;
import com.gitcanvas.model.RepoInfo;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Calls Gemini API with the analysis data and a carefully crafted prompt.
     * Returns three separate files: HTML, CSS, JS.
     *
     * The prompt went through ~5 iterations during development:
     *  - v1: Generic output, looked like a Bootstrap template
     *  - v2: Better structure but AI-sounding copy ("passionate developer...")
     *  - v3: Added banned phrases list, copy improved
     *  - v4: Added specific CSS direction (custom properties, clamp, grid)
     *  - v5: Added JSON output format + tech-stack-based design personality
     */
    public PortfolioFiles generatePortfolio(AnalysisResult analysis) {
        log.info("Generating portfolio for: {}", analysis.getProfile().getLogin());

        String prompt = buildPrompt(analysis);
        String responseText = callGemini(prompt);
        PortfolioFiles files = parseGeminiResponse(responseText);

        log.info("Portfolio generated successfully. HTML: {} chars, CSS: {} chars, JS: {} chars",
                files.getHtml().length(), files.getCss().length(), files.getJs().length());

        return files;
    }

    // ---------------------------------------------------------------
    //  Gemini API call
    // ---------------------------------------------------------------

    private String callGemini(String prompt) {
        String url = GEMINI_URL + "?key=" + geminiApiKey;

        // Build the request body structure that Gemini expects
        String requestBody = """
                {
                  "contents": [
                    {
                      "parts": [
                        { "text": %s }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "temperature": 0.8,
                    "maxOutputTokens": 20000,
                    "responseMimeType": "application/json"
                  }
                }
                """.formatted(objectMapper.valueToTree(prompt).toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        log.info("Calling Gemini API...");
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);

        if (response.getBody() == null) {
            throw new RuntimeException("Empty response from Gemini API");
        }

        // Extract the text content from Gemini's response structure:
        // { candidates: [{ content: { parts: [{ text: "..." }] } }] }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidates = root.path("candidates");

            if (candidates.isEmpty()) {
                throw new RuntimeException("No candidates in Gemini response");
            }

            String text = candidates.get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            return text;
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }

    // ---------------------------------------------------------------
    //  Response parsing
    // ---------------------------------------------------------------

    /**
     * Parses the JSON response from Gemini into our PortfolioFiles object.
     *
     * Gemini sometimes wraps JSON in markdown code fences even when you ask it
     * not to. We strip those if present. We also handle the case where Gemini
     * adds explanatory text before/after the JSON by finding the first { and
     * last }.
     */
    private PortfolioFiles parseGeminiResponse(String responseText) {
        // Strip markdown code fences if present
        String cleaned = responseText.trim();
        if (cleaned.startsWith("```")) {
            // Remove opening fence (```json or ```)
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
        }
        cleaned = cleaned.trim();

        // Find JSON boundaries — look for the outermost { }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        try {
            JsonNode json = objectMapper.readTree(cleaned);

            String html = json.has("html") ? json.get("html").asText() : "";
            String css = json.has("css") ? json.get("css").asText() : "";
            String js = json.has("js") ? json.get("js").asText() : "";

            if (html.isEmpty()) {
                throw new RuntimeException("Generated HTML is empty");
            }

            return new PortfolioFiles(html, css, js);
        } catch (Exception e) {
            log.error("Failed to parse portfolio JSON. Response preview: {}",
                    cleaned.substring(0, Math.min(500, cleaned.length())));
            throw new RuntimeException("Failed to parse generated portfolio: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    //  Prompt construction
    // ---------------------------------------------------------------

    private String buildPrompt(AnalysisResult analysis) {
        var profile = analysis.getProfile();
        var topRepos = analysis.getTopRepos();
        var langStats = analysis.getLanguageStats();
        var commits = analysis.getMonthlyCommits();

        // Build the structured data section
        StringBuilder data = new StringBuilder();
        data.append("=== DEVELOPER DATA ===\n");
        data.append("Name: ").append(safe(profile.getName(), profile.getLogin())).append("\n");
        data.append("Username: ").append(profile.getLogin()).append("\n");
        data.append("Avatar URL: ").append(safe(profile.getAvatarUrl(), "")).append("\n");
        data.append("Bio: ").append(safe(profile.getBio(), "Not provided")).append("\n");
        data.append("Location: ").append(safe(profile.getLocation(), "Not specified")).append("\n");
        data.append("Company: ").append(safe(profile.getCompany(), "")).append("\n");
        data.append("Website/Blog: ").append(safe(profile.getBlog(), "")).append("\n");
        data.append("Twitter: ").append(safe(profile.getTwitterUsername(), "")).append("\n");
        data.append("GitHub URL: ").append(profile.getHtmlUrl()).append("\n");
        data.append("Total public repos: ").append(profile.getPublicRepos()).append("\n");
        data.append("Total stars earned: ").append(analysis.getTotalStars()).append("\n");
        data.append("Total forks earned: ").append(analysis.getTotalForks()).append("\n");
        data.append("Followers: ").append(profile.getFollowers()).append("\n");
        data.append("Following: ").append(profile.getFollowing()).append("\n");
        data.append("Primary language: ").append(analysis.getPrimaryLanguage()).append("\n\n");

        // Languages breakdown
        data.append("Language distribution:\n");
        int totalLangRepos = langStats.values().stream().mapToInt(i -> i).sum();
        for (var entry : langStats.entrySet()) {
            int pct = (int) Math.round(100.0 * entry.getValue() / totalLangRepos);
            data.append("  - ").append(entry.getKey()).append(": ")
                    .append(entry.getValue()).append(" repos (").append(pct).append("%)\n");
        }

        // Top repos
        data.append("\nFeatured repositories (ranked by stars, forks, and recency):\n");
        for (int i = 0; i < topRepos.size(); i++) {
            RepoInfo repo = topRepos.get(i);
            data.append(String.format("  %d. %s — %s | ★ %d | Forks: %d | Language: %s | %s\n",
                    i + 1,
                    repo.getName(),
                    safe(repo.getDescription(), "No description"),
                    repo.getStars(),
                    repo.getForks(),
                    safe(repo.getLanguage(), "—"),
                    repo.getHtmlUrl()));
        }

        // Commit activity
        if (!commits.isEmpty()) {
            data.append("\nRecent commit activity:\n");
            for (var entry : commits.entrySet()) {
                data.append("  - ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append(" commits\n");
            }
        }

        // Figure out the design direction based on primary language
        String designDirection = getDesignDirection(analysis.getPrimaryLanguage(), langStats);

        // Build the full prompt
        return """
                You are a senior frontend developer who hand-crafts portfolio websites that look like they were built by a skilled human, not generated by AI. You have strong opinions about design and you write clean, well-structured code.

                Generate a complete developer portfolio website as THREE separate files (index.html, style.css, script.js) for the developer described below.

                %s

                === DESIGN DIRECTION ===
                %s

                === CRITICAL DESIGN RULES ===
                1. The portfolio must NOT look AI-generated or template-like. No Bootstrap, no generic hero sections with centered text on a gradient.
                2. BANNED PHRASES in any text content: "passionate developer", "innovative solutions", "cutting-edge", "leveraging", "constantly learning", "driven professional", "I strive to", "dedicated to delivering", "seeking opportunities". These are dead giveaways of AI-generated content.
                3. Write the About section in first person. Be specific — reference their actual projects by name, mention technologies they actually use, and describe patterns you see in their repos. Sound like a real person who's a bit casual but professional.
                4. If no bio was provided, write one based entirely on what their repositories and language usage reveal about them.
                5. Project descriptions should sound natural. If a repo has no description, write a brief one based on the repo name and language.
                6. Design should feel intentional and cohesive — like someone spent time on it, not like it was auto-generated.

                === TECHNICAL REQUIREMENTS ===
                1. index.html must link to "style.css" in the head and "script.js" before closing body tag, using relative paths.
                2. Use Google Fonts — load them via <link> in the HTML head. Pick 2 complementary fonts based on the design direction.
                3. All layout must use CSS Grid and/or Flexbox. No floats, no tables for layout.
                4. Use CSS custom properties (variables) for all colors, spacing, and font families. Define them in :root.
                5. Use clamp() for responsive font sizes instead of media queries where possible.
                6. Make it fully responsive — looks great on mobile (360px), tablet (768px), and desktop (1200px+).
                7. Add a smooth scroll behavior.
                8. Include a favicon using an inline SVG data URI in a <link> tag.
                9. Do NOT use any external CSS or JS libraries/frameworks. Pure CSS and vanilla JS only.

                === SECTIONS REQUIRED ===
                1. **Sticky Header**: Developer name/logo on the left, nav links on the right. Collapses to hamburger on mobile.
                2. **Hero Section**: Developer name (large), short tagline, avatar image (use the actual avatar URL), location if available. Make it visually striking but not generic.
                3. **About Me**: 2-3 paragraphs about the developer based on their GitHub activity. Specific, personal, referencing actual repo names and technologies.
                4. **Tech Stack / Skills**: Display all detected languages as styled badges or bars with visual weight indicators. More-used languages should appear more prominent.
                5. **Featured Projects**: Cards for each top repo. Each card must include: project name (as a link to the GitHub repo), description, star count, fork count, and a language tag. Cards should have hover effects.
                6. **GitHub Stats**: Display total repositories, total stars, total forks, and followers as stat cards or counters.
                7. **Footer**: GitHub profile link, website/blog link if available, twitter if available. Keep it simple.

                === JAVASCRIPT REQUIREMENTS ===
                1. Implement a scroll-based reveal animation using Intersection Observer for sections and project cards.
                2. Add a header scroll effect (shadow or background change on scroll).
                3. Implement smooth scroll for anchor links.
                4. Add a mobile navigation toggle (hamburger menu).
                5. Optional: Add a subtle typing effect for the hero tagline, or a counter animation for the stats.
                6. Keep JS clean and well-organized. Use 'use strict'. No jQuery, no frameworks.

                === OUTPUT FORMAT ===
                Respond with a JSON object containing exactly these three keys:
                {
                  "html": "complete index.html content as a string",
                  "css": "complete style.css content as a string",
                  "js": "complete script.js content as a string"
                }

                Do NOT wrap the response in markdown code fences.
                Do NOT add any text before or after the JSON.
                Ensure all string values are properly escaped for JSON (especially quotes and newlines within the code).
                """.formatted(data.toString(), designDirection);
    }

    /**
     * Returns a design direction paragraph based on the developer's
     * primary language / tech stack. This is what gives each portfolio
     * a personality that matches the developer's identity.
     */
    private String getDesignDirection(String primaryLanguage, Map<String, Integer> langStats) {
        if (primaryLanguage == null) primaryLanguage = "Unknown";

        // Check for common language families
        boolean hasSystems = langStats.containsKey("Rust") || langStats.containsKey("C")
                || langStats.containsKey("C++") || langStats.containsKey("Go");
        boolean hasWeb = langStats.containsKey("JavaScript") || langStats.containsKey("TypeScript")
                || langStats.containsKey("HTML") || langStats.containsKey("CSS");
        boolean hasData = langStats.containsKey("Python") || langStats.containsKey("Jupyter Notebook")
                || langStats.containsKey("R");

        return switch (primaryLanguage) {
            case "Python" -> """
                    Style: Clean, analytical, professional. Think data-science elegance.
                    Color palette: Dark navy background (#0f172a), white text, slate surfaces (#1e293b), soft blue accent (#38bdf8).
                    Fonts: "Inter" for body, "Source Serif 4" for headings.
                    Vibe: Confident and technical. Subtle use of monospace for code-related elements. Clean card borders, generous whitespace. No flashy gradients.
                    """;

            case "JavaScript", "TypeScript" -> """
                    Style: Modern, vibrant, tech-forward. Think premium developer tool.
                    Color palette: Near-black background (#0a0a0a), warm white text (#fafafa), accent amber (#f59e0b) or cyan (#06b6d4), card surfaces (#141414).
                    Fonts: "Inter" for body, "Space Grotesk" for headings.
                    Vibe: High-energy but tasteful. Subtle gradient accents on hover states. Rounded corners. Smooth transitions. A touch of personality without being loud.
                    """;

            case "Java", "Kotlin", "C#" -> """
                    Style: Professional, structured, modern-corporate. Think polished but not boring.
                    Color palette: Light background (#fafaf9), dark text (#1c1917), subtle warm border (#e7e5e4), royal blue accent (#2563eb).
                    Fonts: "Inter" for body, "DM Serif Display" for headings.
                    Vibe: Trustworthy and structured. Clean typography hierarchy. Professional without being stiff. Minimal use of color — accent only for interactive elements. Subtle shadows.
                    """;

            case "Rust", "C", "C++", "Go" -> """
                    Style: Minimal, terminal-inspired, developer-focused. Think hacker aesthetic done tastefully.
                    Color palette: Deep dark background (#0d1117), green accent (#39d353) or cyan (#58a6ff), surface (#161b22), muted text (#8b949e).
                    Fonts: "JetBrains Mono" for headings and accents, "Inter" for body text.
                    Vibe: Technical and precise. Monospace elements feel natural. Minimal decoration. Borders over shadows. A terminal prompt inspired hero area. Low-key but impressive.
                    """;

            case "Ruby", "PHP" -> """
                    Style: Warm, approachable, modern web aesthetic. Think indie developer.
                    Color palette: Warm white background (#fef7f0), dark text (#1a1a1a), rose accent (#e11d48), warm gray surfaces (#f5f0eb).
                    Fonts: "Inter" for body, "Outfit" for headings.
                    Vibe: Friendly and personal. Rounded elements, warm tones, inviting. Not corporate. Feels like someone who enjoys what they build.
                    """;

            default -> """
                    Style: Clean, modern, versatile. Think polished portfolio of a well-rounded developer.
                    Color palette: Off-white background (#f8fafc), dark text (#0f172a), indigo accent (#6366f1), light surface (#f1f5f9).
                    Fonts: "Inter" for body, "Sora" for headings.
                    Vibe: Professional but with personality. Balanced use of whitespace and color. Feels curated. Neither too flashy nor too minimal. The kind of portfolio that works across industries.
                    """;
        };
    }

    private String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
