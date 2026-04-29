/* ============================================
   GitCanvas — Landing Page JavaScript
   ============================================ */

(function () {
    'use strict';

    // -- DOM References --
    const header = document.getElementById('site-header');
    const mobileToggle = document.getElementById('mobile-menu-toggle');
    const mobileOverlay = document.getElementById('mobile-nav-overlay');
    const heroInput = document.getElementById('github-username');
    const bottomInput = document.getElementById('github-username-bottom');
    const generateBtn = document.getElementById('generate-btn');
    const generateBtnBottom = document.getElementById('generate-btn-bottom');
    const previewBrowser = document.querySelector('.preview-browser');
    const themeBtns = document.querySelectorAll('.theme-btn');
    const mobileNavLinks = document.querySelectorAll('.mobile-nav-link');

    // -- Header scroll effect --
    var lastScroll = 0;

    function onScroll() {
        var scrollY = window.scrollY || window.pageYOffset;
        if (scrollY > 20) {
            header.classList.add('scrolled');
        } else {
            header.classList.remove('scrolled');
        }
        lastScroll = scrollY;
    }

    window.addEventListener('scroll', onScroll, { passive: true });

    // -- Mobile menu --
    if (mobileToggle) {
        mobileToggle.addEventListener('click', function () {
            mobileToggle.classList.toggle('active');
            mobileOverlay.classList.toggle('active');
            document.body.style.overflow = mobileOverlay.classList.contains('active') ? 'hidden' : '';
        });
    }

    // Close mobile menu on link click
    mobileNavLinks.forEach(function (link) {
        link.addEventListener('click', function () {
            mobileToggle.classList.remove('active');
            mobileOverlay.classList.remove('active');
            document.body.style.overflow = '';
        });
    });

    // -- Generate button action --
    function handleGenerate(inputEl) {
        var username = inputEl.value.trim();
        if (!username) {
            inputEl.focus();
            inputEl.parentElement.style.borderColor = 'var(--color-accent)';
            inputEl.parentElement.style.boxShadow = '0 0 0 3px var(--color-accent-light)';

            setTimeout(function () {
                inputEl.parentElement.style.borderColor = '';
                inputEl.parentElement.style.boxShadow = '';
            }, 2000);
            return;
        }

        // Validate: GitHub usernames can only contain alphanumeric characters or hyphens
        // and cannot start or end with a hyphen
        var usernameRegex = /^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$/;
        if (!usernameRegex.test(username)) {
            showToast('Please enter a valid GitHub username.');
            inputEl.focus();
            return;
        }

        // Navigate to the generator page with the username
        window.location.href = 'generator.html?username=' + encodeURIComponent(username);
    }

    if (generateBtn) {
        generateBtn.addEventListener('click', function () {
            handleGenerate(heroInput);
        });
    }

    if (generateBtnBottom) {
        generateBtnBottom.addEventListener('click', function () {
            handleGenerate(bottomInput);
        });
    }

    // Enter key on inputs
    [heroInput, bottomInput].forEach(function (input) {
        if (input) {
            input.addEventListener('keydown', function (e) {
                if (e.key === 'Enter') {
                    handleGenerate(input);
                }
            });
        }
    });

    // -- Theme switcher --
    themeBtns.forEach(function (btn) {
        btn.addEventListener('click', function () {
            themeBtns.forEach(function (b) {
                b.classList.remove('active');
            });
            btn.classList.add('active');

            var theme = btn.getAttribute('data-theme');
            previewBrowser.className = 'preview-browser';

            if (theme === 'modern') {
                previewBrowser.classList.add('theme-modern');
            } else if (theme === 'hacker') {
                previewBrowser.classList.add('theme-hacker');
            }
            // 'minimal' is the default (no additional class)
        });
    });

    // -- Intersection Observer for reveal animations --
    var revealElements = document.querySelectorAll('.step-item, .feature-block, .preview-browser, .proof-stat');

    if ('IntersectionObserver' in window) {
        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    entry.target.classList.add('reveal', 'visible');
                    observer.unobserve(entry.target);
                }
            });
        }, {
            threshold: 0.15,
            rootMargin: '0px 0px -40px 0px'
        });

        revealElements.forEach(function (el) {
            el.classList.add('reveal');
            observer.observe(el);
        });
    } else {
        // Fallback: just show everything
        revealElements.forEach(function (el) {
            el.style.opacity = '1';
            el.style.transform = 'none';
        });
    }

    // -- Language bar animation --
    var langFills = document.querySelectorAll('.lang-fill');

    if ('IntersectionObserver' in window && langFills.length > 0) {
        var langParent = document.querySelector('.analysis-visual');
        var langObserver = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    langFills.forEach(function (fill, i) {
                        setTimeout(function () {
                            fill.classList.add('animated');
                        }, i * 150);
                    });
                    langObserver.unobserve(entry.target);
                }
            });
        }, { threshold: 0.3 });

        if (langParent) {
            langObserver.observe(langParent);
        }
    }

    // -- Stats counter animation --
    var proofNumbers = document.querySelectorAll('.proof-number');

    function animateCounter(el) {
        var target = parseInt(el.getAttribute('data-target'), 10);
        var suffix = el.getAttribute('data-suffix') || '';
        var duration = 1500;
        var start = 0;
        var startTime = null;

        function step(timestamp) {
            if (!startTime) startTime = timestamp;
            var progress = Math.min((timestamp - startTime) / duration, 1);
            // Ease out cubic
            var ease = 1 - Math.pow(1 - progress, 3);
            var current = Math.floor(ease * target);
            el.textContent = current.toLocaleString() + suffix;
            if (progress < 1) {
                requestAnimationFrame(step);
            } else {
                el.textContent = target.toLocaleString() + suffix;
            }
        }

        requestAnimationFrame(step);
    }

    if ('IntersectionObserver' in window && proofNumbers.length > 0) {
        var counterObserver = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (entry.isIntersecting) {
                    animateCounter(entry.target);
                    counterObserver.unobserve(entry.target);
                }
            });
        }, { threshold: 0.5 });

        proofNumbers.forEach(function (el) {
            counterObserver.observe(el);
        });
    }

    // -- Simple toast notification --
    function showToast(message) {
        var existing = document.querySelector('.toast-notification');
        if (existing) existing.remove();

        var toast = document.createElement('div');
        toast.className = 'toast-notification';
        toast.textContent = message;

        Object.assign(toast.style, {
            position: 'fixed',
            bottom: '24px',
            left: '50%',
            transform: 'translateX(-50%) translateY(10px)',
            background: '#1A1A1A',
            color: '#FAFAF8',
            padding: '12px 24px',
            borderRadius: '8px',
            fontSize: '0.88rem',
            fontFamily: 'var(--font-sans)',
            fontWeight: '500',
            zIndex: '9999',
            opacity: '0',
            transition: 'opacity 0.3s ease, transform 0.3s ease',
            boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
            maxWidth: '90vw',
            textAlign: 'center'
        });

        document.body.appendChild(toast);

        // Trigger animation
        requestAnimationFrame(function () {
            toast.style.opacity = '1';
            toast.style.transform = 'translateX(-50%) translateY(0)';
        });

        setTimeout(function () {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(-50%) translateY(10px)';
            setTimeout(function () {
                toast.remove();
            }, 300);
        }, 3000);
    }

    // -- Smooth anchor links --
    document.querySelectorAll('a[href^="#"]').forEach(function (anchor) {
        anchor.addEventListener('click', function (e) {
            var targetId = this.getAttribute('href');
            if (targetId === '#') return;
            var targetEl = document.querySelector(targetId);
            if (targetEl) {
                e.preventDefault();
                var offsetTop = targetEl.getBoundingClientRect().top + window.pageYOffset - 80;
                window.scrollTo({
                    top: offsetTop,
                    behavior: 'smooth'
                });
            }
        });
    });

})();
