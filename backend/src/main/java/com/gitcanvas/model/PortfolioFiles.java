package com.gitcanvas.model;

/**
 * Holds the three generated portfolio files.
 * Stored in memory on the backend so the frontend iframe
 * can load each file via the preview endpoint.
 */
public class PortfolioFiles {

    private String html;
    private String css;
    private String js;

    public PortfolioFiles() {}

    public PortfolioFiles(String html, String css, String js) {
        this.html = html;
        this.css = css;
        this.js = js;
    }

    public String getHtml() { return html; }
    public void setHtml(String html) { this.html = html; }

    public String getCss() { return css; }
    public void setCss(String css) { this.css = css; }

    public String getJs() { return js; }
    public void setJs(String js) { this.js = js; }
}
