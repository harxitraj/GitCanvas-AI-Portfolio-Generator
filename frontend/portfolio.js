/* ============================================
   GitCanvas — Portfolio Viewer JavaScript
   ============================================

   Handles two modes:
   1. Preview-only (default) — full-width iframe showing the generated portfolio
   2. Editor mode — split-pane with Monaco editor (left) and preview (right)

   Key concepts:
   - committedFiles: what the backend currently has (last saved state)
   - Monaco models: the working draft the user is editing
   - isDirty flag: tracks if any model has been modified since last save
   ============================================ */

(function () {
    'use strict';

    // -- Configuration --
    var API_BASE = 'http://localhost:8080';
    var MONACO_CDN = 'https://cdn.jsdelivr.net/npm/monaco-editor@0.52.2/min';

    // -- State --
    var username = null;
    var editor = null;               // Monaco editor instance
    var models = {};                 // { html: ITextModel, css: ITextModel, js: ITextModel }
    var committedFiles = null;       // { html: '...', css: '...', js: '...' } — last saved state
    var activeTab = 'html';          // which tab is currently active
    var isDirty = false;             // has anything been edited since last save?
    var monacoLoaded = false;        // has Monaco finished loading?
    var isEditorMode = false;        // are we in split-pane editor mode?

    // -- DOM References --
    var iframe = document.getElementById('portfolio-iframe');
    var iframeSplit = document.getElementById('portfolio-iframe-split');
    var iframeLoading = document.getElementById('iframe-loading');
    var addrText = document.getElementById('addr-text');
    var addrTextSplit = document.getElementById('addr-text-split');
    var toolbarUsername = document.getElementById('toolbar-username');

    // Buttons
    var viewCodeBtn = document.getElementById('btn-view-code');
    var downloadZipBtn = document.getElementById('btn-download-zip');
    var regenerateBtn = document.getElementById('btn-regenerate');
    var backPreviewBtn = document.getElementById('btn-back-preview');
    var discardBtn = document.getElementById('btn-discard');
    var finalizeBtn = document.getElementById('btn-finalize');

    // Containers
    var previewContainer = document.getElementById('preview-container');
    var splitPane = document.getElementById('split-pane');
    var editorContainer = document.getElementById('editor-container');
    var editorLoading = document.getElementById('editor-loading');

    // Tabs
    var fileTabs = document.querySelectorAll('.file-tab');

    // Modal
    var modalOverlay = document.getElementById('modal-overlay');
    var modalConfirm = document.getElementById('modal-confirm');
    var modalCancel = document.getElementById('modal-cancel');

    // Toast
    var toastEl = document.getElementById('toast');
    var toastIcon = document.getElementById('toast-icon');
    var toastMessage = document.getElementById('toast-message');

    // -- Parse URL params --
    var params = new URLSearchParams(window.location.search);
    username = params.get('username');

    if (!username) {
        window.location.href = 'index.html';
        return;
    }

    toolbarUsername.textContent = '@' + username;


    /* ============================================
       Preview Loading
       ============================================ */

    function loadPreview() {
        var previewUrl = API_BASE + '/api/portfolio/preview/' + username + '/index.html?v=' + Date.now();
        var displayUrl = username + '-portfolio.gitcanvas.dev';

        addrText.textContent = displayUrl;
        addrTextSplit.textContent = displayUrl;

        // Show loading in the full preview mode
        iframeLoading.classList.remove('hidden');

        iframe.src = previewUrl;
        iframe.onload = function () {
            setTimeout(function () {
                iframeLoading.classList.add('hidden');
            }, 400);
        };

        // Also update split-pane iframe if it exists
        iframeSplit.src = previewUrl;
    }

    // Start loading immediately
    loadPreview();


    /* ============================================
       Fetch Portfolio Files for Editor
       ============================================ */

    function fetchPortfolioFiles(callback) {
        fetch(API_BASE + '/api/portfolio/files/' + username)
            .then(function (res) {
                if (!res.ok) throw new Error('Failed to fetch files');
                return res.json();
            })
            .then(function (data) {
                committedFiles = {
                    html: data.html || '',
                    css: data.css || '',
                    js: data.js || ''
                };
                callback(null, committedFiles);
            })
            .catch(function (err) {
                callback(err, null);
            });
    }


    /* ============================================
       Monaco Editor Initialization
       ============================================ */

    function initMonaco(files) {
        if (monacoLoaded) {
            // Monaco already loaded — just update models if needed
            updateModelsFromFiles(files);
            showEditor();
            return;
        }

        // Configure the AMD loader to find Monaco
        require.config({
            paths: { 'vs': MONACO_CDN + '/vs' }
        });

        // Load Monaco
        require(['vs/editor/editor.main'], function () {
            monacoLoaded = true;

            // Create one model per file — each has its own undo/redo stack
            models.html = monaco.editor.createModel(files.html, 'html');
            models.css = monaco.editor.createModel(files.css, 'css');
            models.js = monaco.editor.createModel(files.js, 'javascript');

            // Create the editor instance
            editor = monaco.editor.create(editorContainer, {
                model: models.html,
                theme: 'vs-dark',
                fontSize: 13,
                fontFamily: "'Cascadia Code', 'Fira Code', 'JetBrains Mono', Consolas, monospace",
                lineNumbers: 'on',
                minimap: { enabled: true, scale: 1 },
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                automaticLayout: false,
                tabSize: 2,
                renderWhitespace: 'selection',
                bracketPairColorization: { enabled: true },
                padding: { top: 12 },
                smoothScrolling: true,
                cursorBlinking: 'smooth',
                cursorSmoothCaretAnimation: 'on'
            });

            // Attach change listeners to all models for dirty tracking
            models.html.onDidChangeContent(function () { markDirty(); });
            models.css.onDidChangeContent(function () { markDirty(); });
            models.js.onDidChangeContent(function () { markDirty(); });

            // Hide loading state
            editorLoading.style.display = 'none';

            // Handle window resize — Monaco doesn't auto-resize
            window.addEventListener('resize', function () {
                if (editor) {
                    editor.layout();
                }
            });

            showEditor();
        });
    }

    /**
     * If Monaco is already loaded and we need to reset models
     * (e.g., after regenerating portfolio)
     */
    function updateModelsFromFiles(files) {
        if (models.html) models.html.setValue(files.html);
        if (models.css) models.css.setValue(files.css);
        if (models.js) models.js.setValue(files.js);
        clearDirty();
    }

    /**
     * Show the editor after Monaco is ready
     */
    function showEditor() {
        // Wait one frame for CSS to settle, then tell Monaco to layout
        requestAnimationFrame(function () {
            if (editor) {
                editor.layout();
            }
        });
    }


    /* ============================================
       Tab Switching
       ============================================ */

    function switchTab(newTab) {
        if (newTab === activeTab || !models[newTab]) return;

        activeTab = newTab;

        // Update tab visual state
        fileTabs.forEach(function (tab) {
            tab.classList.toggle('active', tab.getAttribute('data-file') === newTab);
        });

        // Swap the model — Monaco preserves cursor position and undo/redo per model
        if (editor) {
            editor.setModel(models[newTab]);
            editor.focus();
        }
    }

    // Attach click handlers to tabs
    fileTabs.forEach(function (tab) {
        tab.addEventListener('click', function () {
            var file = tab.getAttribute('data-file');
            switchTab(file);
        });
    });


    /* ============================================
       Dirty State Tracking
       ============================================ */

    function markDirty() {
        if (isDirty) return; // already dirty
        isDirty = true;
        finalizeBtn.disabled = false;
        discardBtn.disabled = false;
        finalizeBtn.classList.add('pulse');
    }

    function clearDirty() {
        isDirty = false;
        finalizeBtn.disabled = true;
        discardBtn.disabled = true;
        finalizeBtn.classList.remove('pulse');
    }

    // Warn user if they try to leave with unsaved changes
    window.addEventListener('beforeunload', function (e) {
        if (isDirty) {
            e.preventDefault();
            e.returnValue = '';
        }
    });


    /* ============================================
       View Toggling (Preview-Only ↔ Editor)
       ============================================ */

    function enterEditorMode() {
        if (isEditorMode) return;
        isEditorMode = true;

        // Toggle button states
        viewCodeBtn.classList.add('active-btn');
        viewCodeBtn.querySelector('span').textContent = 'Editing';

        // Switch layouts
        previewContainer.style.display = 'none';
        splitPane.style.display = 'flex';

        // If files aren't fetched yet, fetch them
        if (!committedFiles) {
            fetchPortfolioFiles(function (err, files) {
                if (err) {
                    showToast('Failed to load portfolio files', 'error');
                    exitEditorMode();
                    return;
                }
                initMonaco(files);
            });
        } else {
            initMonaco(committedFiles);
        }
    }

    function exitEditorMode() {
        if (!isEditorMode) return;

        // Check for unsaved changes
        if (isDirty) {
            var leave = confirm('You have unsaved changes. Are you sure you want to exit the editor?');
            if (!leave) return;
        }

        isEditorMode = false;

        // Toggle button states
        viewCodeBtn.classList.remove('active-btn');
        viewCodeBtn.querySelector('span').textContent = 'View Code';

        // Switch layouts
        splitPane.style.display = 'none';
        previewContainer.style.display = 'flex';

        // Reload preview in case changes were committed
        loadPreview();

        // Reset dirty state if user chose to leave
        clearDirty();
    }

    // Button handlers
    viewCodeBtn.addEventListener('click', function () {
        if (isEditorMode) {
            exitEditorMode();
        } else {
            enterEditorMode();
        }
    });

    backPreviewBtn.addEventListener('click', function () {
        exitEditorMode();
    });


    /* ============================================
       Finalize Changes Flow
       ============================================ */

    finalizeBtn.addEventListener('click', function () {
        if (!isDirty) return;
        showModal();
    });

    modalCancel.addEventListener('click', function () {
        hideModal();
    });

    modalOverlay.addEventListener('click', function (e) {
        if (e.target === modalOverlay) {
            hideModal();
        }
    });

    modalConfirm.addEventListener('click', function () {
        commitChanges();
    });

    function showModal() {
        modalOverlay.style.display = 'flex';
    }

    function hideModal() {
        modalOverlay.style.display = 'none';
        modalConfirm.classList.remove('loading');
    }

    /**
     * Send the current editor content to the backend.
     * On success, update committedFiles and reload the preview.
     */
    function commitChanges() {
        modalConfirm.classList.add('loading');
        modalConfirm.innerHTML = '<span>Applying…</span>';

        // Read content from all three Monaco models
        var updatedFiles = {
            html: models.html.getValue(),
            css: models.css.getValue(),
            js: models.js.getValue()
        };

        fetch(API_BASE + '/api/portfolio/update/' + username, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(updatedFiles)
        })
        .then(function (res) {
            if (!res.ok) throw new Error('Update failed');
            return res.json();
        })
        .then(function () {
            // Update committed state
            committedFiles = {
                html: updatedFiles.html,
                css: updatedFiles.css,
                js: updatedFiles.js
            };

            clearDirty();
            hideModal();

            // Reset confirm button text
            modalConfirm.innerHTML =
                '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>' +
                ' Yes, apply changes';

            // Reload preview iframe with cache buster
            var previewUrl = API_BASE + '/api/portfolio/preview/' + username + '/index.html?v=' + Date.now();
            iframeSplit.src = previewUrl;
            iframe.src = previewUrl;

            showToast('Portfolio updated successfully', 'success');
        })
        .catch(function (err) {
            hideModal();

            // Reset confirm button text
            modalConfirm.innerHTML =
                '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>' +
                ' Yes, apply changes';

            showToast('Failed to update portfolio: ' + err.message, 'error');
        });
    }


    /* ============================================
       Discard Changes
       ============================================ */

    discardBtn.addEventListener('click', function () {
        if (!isDirty) return;

        var discard = confirm('Discard all changes and revert to the last saved version?');
        if (!discard) return;

        // Reset all models to committed state
        if (committedFiles) {
            models.html.setValue(committedFiles.html);
            models.css.setValue(committedFiles.css);
            models.js.setValue(committedFiles.js);
        }

        clearDirty();
        showToast('Changes discarded', 'success');
    });


    /* ============================================
       Download ZIP
       ============================================ */

    downloadZipBtn.addEventListener('click', function () {
        // If files haven't been fetched yet, fetch them first
        if (!committedFiles) {
            downloadZipBtn.disabled = true;
            fetchPortfolioFiles(function (err, files) {
                downloadZipBtn.disabled = false;
                if (err) {
                    showToast('Failed to load files for download', 'error');
                    return;
                }
                generateAndDownloadZip(files);
            });
        } else {
            generateAndDownloadZip(committedFiles);
        }
    });

    /**
     * Create a ZIP file using JSZip and trigger a browser download.
     * We download the committed files (not the draft) so users
     * never download broken half-edited code.
     */
    function generateAndDownloadZip(files) {
        if (typeof JSZip === 'undefined') {
            showToast('ZIP library not loaded. Please try again.', 'error');
            return;
        }

        var zip = new JSZip();
        var folderName = username + '-portfolio';
        var folder = zip.folder(folderName);

        folder.file('index.html', files.html);
        folder.file('style.css', files.css);
        folder.file('script.js', files.js);

        // Add a simple README
        var readme = '# ' + username + ' — Developer Portfolio\n\n'
            + 'Generated by GitCanvas (https://github.com/YOUR_USERNAME/GitCanvas-AI-Portfolio-Generator)\n\n'
            + '## Files\n'
            + '- index.html — Portfolio page\n'
            + '- style.css — Styles\n'
            + '- script.js — Interactions\n\n'
            + '## How to view\n'
            + 'Open index.html in any web browser.\n';
        folder.file('README.md', readme);

        zip.generateAsync({ type: 'blob' })
            .then(function (blob) {
                var url = URL.createObjectURL(blob);
                var a = document.createElement('a');
                a.href = url;
                a.download = folderName + '.zip';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);

                showToast('Portfolio downloaded as ZIP', 'success');
            })
            .catch(function () {
                showToast('Failed to generate ZIP', 'error');
            });
    }


    /* ============================================
       Regenerate Button
       ============================================ */

    regenerateBtn.addEventListener('click', function () {
        if (isDirty) {
            var leave = confirm('You have unsaved changes. Regenerating will discard them. Continue?');
            if (!leave) return;
        }
        window.location.href = 'generator.html?username=' + encodeURIComponent(username);
    });


    /* ============================================
       Toast Notifications
       ============================================ */

    var toastTimeout = null;

    function showToast(message, type) {
        // Clear any existing timeout
        if (toastTimeout) {
            clearTimeout(toastTimeout);
        }

        // Set content
        toastMessage.textContent = message;

        // Set icon based on type
        if (type === 'success') {
            toastIcon.textContent = '✓';
            toastEl.className = 'toast toast-success';
        } else if (type === 'error') {
            toastIcon.textContent = '✕';
            toastEl.className = 'toast toast-error';
        } else {
            toastIcon.textContent = 'ℹ';
            toastEl.className = 'toast';
        }

        // Show
        toastEl.style.display = 'flex';
        requestAnimationFrame(function () {
            toastEl.classList.add('visible');
        });

        // Auto-dismiss after 3.5 seconds
        toastTimeout = setTimeout(function () {
            toastEl.classList.remove('visible');
            setTimeout(function () {
                toastEl.style.display = 'none';
            }, 300);
        }, 3500);
    }


    /* ============================================
       Keyboard Shortcuts
       ============================================ */

    document.addEventListener('keydown', function (e) {
        // Ctrl+S / Cmd+S — finalize changes (when in editor mode)
        if ((e.ctrlKey || e.metaKey) && e.key === 's') {
            if (isEditorMode && isDirty) {
                e.preventDefault();
                showModal();
            }
        }

        // Escape — close modal
        if (e.key === 'Escape') {
            if (modalOverlay.style.display === 'flex') {
                hideModal();
            }
        }
    });

})();
