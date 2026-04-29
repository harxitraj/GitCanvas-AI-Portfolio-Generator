/* ============================================
   GitCanvas — Generator Page JavaScript
   ============================================ */

(function () {
    'use strict';

    // -- Configuration --
    var API_BASE = 'http://localhost:8080';

    // -- DOM refs --
    var loadingEl = document.getElementById('gen-loading');
    var errorEl = document.getElementById('gen-error');
    var avatarEl = document.getElementById('gen-avatar');
    var avatarRing = document.querySelector('.gen-avatar-ring');
    var usernameDisplay = document.getElementById('gen-username-display');
    var subtitleEl = document.getElementById('gen-subtitle');
    var steps = document.querySelectorAll('.gen-step');
    var retryBtn = document.getElementById('error-retry');
    var errorTitle = document.getElementById('error-title');
    var errorMessage = document.getElementById('error-message');

    // -- Get username from URL --
    var params = new URLSearchParams(window.location.search);
    var username = params.get('username');

    if (!username || !username.trim()) {
        window.location.href = 'index.html';
        return;
    }

    username = username.trim().toLowerCase();
    usernameDisplay.textContent = '@' + username;

    // -- Step management --
    var currentStep = 0;

    function activateStep(stepNumber) {
        steps.forEach(function (step) {
            var num = parseInt(step.getAttribute('data-step'), 10);
            if (num < stepNumber) {
                step.classList.remove('active');
                step.classList.add('complete');
            } else if (num === stepNumber) {
                step.classList.add('active');
                step.classList.remove('complete');
            } else {
                step.classList.remove('active', 'complete');
            }
        });
        currentStep = stepNumber;
    }

    // -- Error display --
    function showError(title, message) {
        loadingEl.style.display = 'none';
        errorEl.style.display = 'flex';
        errorTitle.textContent = title;
        errorMessage.textContent = message;
    }

    // -- Retry button --
    if (retryBtn) {
        retryBtn.addEventListener('click', function () {
            // Restart the whole process
            errorEl.style.display = 'none';
            loadingEl.style.display = 'flex';
            startGeneration();
        });
    }

    // -- Main generation flow --
    function startGeneration() {
        // Reset steps
        steps.forEach(function (step) {
            step.classList.remove('active', 'complete');
        });

        // Step 1: Fetching GitHub profile
        activateStep(1);

        // Call the analyze endpoint
        fetch(API_BASE + '/api/analyze/' + encodeURIComponent(username))
            .then(function (response) {
                if (!response.ok) {
                    return response.json().then(function (data) {
                        throw new Error(data.error || 'Failed to fetch profile');
                    });
                }
                return response.json();
            })
            .then(function (analysisData) {
                // Update avatar if available
                if (analysisData.profile && analysisData.profile.avatarUrl) {
                    avatarEl.style.backgroundImage = 'url(' + analysisData.profile.avatarUrl + ')';
                    avatarRing.classList.add('loaded');
                }

                // Update username display with real name
                if (analysisData.profile && analysisData.profile.name) {
                    usernameDisplay.textContent = analysisData.profile.name;
                }

                // Step 2: Analyzing repositories
                activateStep(2);

                return new Promise(function (resolve) {
                    // Small delay so the user can read each step
                    setTimeout(function () {
                        activateStep(3);
                        setTimeout(function () {
                            resolve(analysisData);
                        }, 800);
                    }, 800);
                });
            })
            .then(function () {
                // Step 4: Generating portfolio with AI
                activateStep(4);
                subtitleEl.textContent = 'AI is crafting your portfolio…';

                return fetch(API_BASE + '/api/portfolio/generate', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username: username })
                });
            })
            .then(function (response) {
                if (!response.ok) {
                    return response.json().then(function (data) {
                        throw new Error(data.error || 'Failed to generate portfolio');
                    });
                }
                return response.json();
            })
            .then(function (genResult) {
                // Step 5: Almost ready
                activateStep(5);
                subtitleEl.textContent = 'Finalizing…';

                // Store the preview URL and navigate after a brief moment
                var previewUrl = genResult.previewUrl;

                setTimeout(function () {
                    // Navigate to the portfolio viewer
                    window.location.href = 'portfolio.html?username=' +
                        encodeURIComponent(username) +
                        '&preview=' + encodeURIComponent(previewUrl);
                }, 1000);
            })
            .catch(function (error) {
                console.error('Generation failed:', error);

                // Show user-friendly error messages based on what went wrong
                var msg = error.message || '';

                if (msg.indexOf('not found') !== -1) {
                    showError(
                        'User not found',
                        'The GitHub username "' + username + '" doesn\'t exist. Please check the spelling and try again.'
                    );
                } else if (msg.indexOf('rate limit') !== -1) {
                    showError(
                        'Rate limit exceeded',
                        'GitHub\'s API rate limit has been reached. Please wait a few minutes and try again.'
                    );
                } else if (msg.indexOf('Failed to fetch') !== -1 || msg.indexOf('NetworkError') !== -1) {
                    showError(
                        'Connection failed',
                        'Could not connect to the server. Make sure the backend is running on localhost:8080.'
                    );
                } else {
                    showError(
                        'Something went wrong',
                        msg || 'An unexpected error occurred while generating your portfolio. Please try again.'
                    );
                }
            });
    }

    // Start the process
    startGeneration();

})();
