<<<<<<< HEAD
# GitCanvas

**AI-Powered GitHub Activity Analyzer & Developer Portfolio Generator**

GitCanvas reads your GitHub profile — repositories, languages, contributions, stars — and generates a polished developer portfolio website you can preview, customize, and deploy.

![Tech Stack](https://img.shields.io/badge/Frontend-HTML%20%7C%20CSS%20%7C%20JS-orange)
![Tech Stack](https://img.shields.io/badge/Backend-Spring%20Boot%203.3-green)
![API](https://img.shields.io/badge/AI-Gemini%202.0%20Flash-blue)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## What it does

1. **Enter your GitHub username** — that's all we need.
2. **We analyze your profile** — repos, languages, stars, forks, commit activity, contribution patterns.
3. **AI generates a portfolio** — Gemini crafts a multi-file portfolio (HTML + CSS + JS) tailored to your tech stack. A Python developer gets a different aesthetic than a JavaScript developer.
4. **Preview it instantly** — see your generated portfolio rendered in-browser.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | HTML, CSS, Vanilla JavaScript |
| Backend | Java 17+, Spring Boot 3.3, REST APIs |
| AI Generation | Google Gemini 2.0 Flash (free tier) |
| GitHub Data | GitHub REST API v3 |

## Project Structure

```
AI-Project/
├── frontend/                   # Static frontend (no build step)
│   ├── index.html              # Landing page
│   ├── styles.css              # Landing page styles
│   ├── script.js               # Landing page interactions
│   ├── generator.html          # Portfolio generation loading page
│   ├── generator.css
│   ├── generator.js
│   ├── portfolio.html          # Portfolio preview viewer
│   ├── portfolio.css
│   └── portfolio.js
├── backend/                    # Spring Boot API
│   ├── pom.xml
│   └── src/main/java/com/gitcanvas/
│       ├── GitCanvasApplication.java
│       ├── config/             # CORS, RestTemplate config
│       ├── controller/         # REST endpoints
│       ├── model/              # Data models
│       └── service/            # GitHub API, Gemini API, orchestration
├── .env.example                # Required environment variables
├── .gitignore
└── README.md
```

## Getting Started

### Prerequisites

- **Java 17+** (tested with Java 24)
- **Maven 3.8+**
- A **GitHub Personal Access Token** (free)
- A **Gemini API Key** (free)

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/AI-Project.git
cd AI-Project
```

### 2. Get your API keys

**GitHub PAT (free):**
1. Go to GitHub → Settings → Developer Settings → Personal Access Tokens → Fine-grained tokens
2. Click "Generate new token"
3. Select **"Public Repositories (read-only)"**
4. Copy the token

**Gemini API Key (free):**
1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Click "Create API Key"
3. Copy the key — no credit card required

### 3. Configure the backend

```bash
cd backend/src/main/resources/
```

Open `application-local.properties` and replace the placeholders with your actual keys:

```properties
github.api.token=ghp_your_actual_token_here
gemini.api.key=your_actual_gemini_key_here
```

> **Note:** This file is gitignored — your keys will never be committed.

### 4. Start the backend

```bash
cd backend
mvn spring-boot:run
```

Wait for: `Started GitCanvasApplication in X seconds`

### 5. Open the frontend

Open `frontend/index.html` in your browser (or use VS Code Live Server for a better experience).

Enter any GitHub username and click **"Build my portfolio"**.

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/analyze/{username}` | Fetch and analyze a GitHub user's profile |
| POST | `/api/portfolio/generate` | Generate portfolio using Gemini AI |
| GET | `/api/portfolio/preview/{username}/{file}` | Serve generated portfolio files for preview |

## How the scoring algorithm works

Repositories are ranked using:

```
score = (stars × 3) + (forks × 2) + recency_bonus
```

Recency bonus: +15 (updated in last 30 days), +10 (90 days), +5 (180 days).

The top 5 repos by score become "Featured Projects" in the portfolio.

## License

MIT
=======
# GitCanvas-AI-Portfolio-Generator
>>>>>>> 4a9a06fb176a493326bfbef008ae7949b9dfad1d
