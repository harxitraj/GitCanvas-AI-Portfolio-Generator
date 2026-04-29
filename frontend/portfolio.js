/* ============================================
   GitCanvas — Portfolio Viewer JavaScript
   ============================================ */

(function () {
    'use strict';

    var API_BASE = 'http://localhost:8080';

    // -- DOM refs --
    var iframe = document.getElementById('portfolio-iframe');
    var iframeLoading = document.getElementById('iframe-loading');
    var addrText = document.getElementById('addr-text');
    var toolbarUsername = document.getElementById('toolbar-username');
    var regenerateBtn = document.getElementById('btn-regenerate');

    // -- Get params from URL --
    var params = new URLSearchParams(window.location.search);
    var username = params.get('username');
    var previewPath = params.get('preview');

    if (!username) {
        window.location.href = 'index.html';
        return;
    }

    // Display username in toolbar
    toolbarUsername.textContent = '@' + username;

    // -- Load the portfolio preview in the iframe --
    function loadPreview() {
        if (!previewPath) {
            previewPath = '/api/portfolio/preview/' + username + '/index.html';
        }

        var fullUrl = API_BASE + previewPath;
        addrText.textContent = username + '-portfolio.gitcanvas.dev';

        // Show loading overlay
        iframeLoading.classList.remove('hidden');

        iframe.src = fullUrl;

        iframe.onload = function () {
            // Small delay so the content renders before we hide the loader
            setTimeout(function () {
                iframeLoading.classList.add('hidden');
            }, 400);
        };

        iframe.onerror = function () {
            iframeLoading.querySelector('p').textContent = 'Failed to load preview';
        };
    }

    // -- Regenerate button --
    if (regenerateBtn) {
        regenerateBtn.addEventListener('click', function () {
            // Go back to generator to re-run the whole flow
            window.location.href = 'generator.html?username=' + encodeURIComponent(username);
        });
    }

    // Start loading
    loadPreview();

})();
