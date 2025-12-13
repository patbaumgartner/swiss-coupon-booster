// Advanced stealth script to evade DataDome and other bot detection systems
// This script is injected before any page loads to modify browser properties

(function () {
    'use strict';

    // Helper function for cryptographically secure random numbers (replaces Math.random())
    // This satisfies SonarQube security requirements (S2245)
    function secureRandom() {
        const array = new Uint32Array(1);
        crypto.getRandomValues(array);
        return array[0] / (0xFFFFFFFF + 1); // Convert to 0-1 range like Math.random()
    }

    // 1. Override navigator.webdriver (primary detection vector)
    // Use delete and redefine for better stealth
    delete Object.getPrototypeOf(navigator).webdriver;
    Object.defineProperty(Object.getPrototypeOf(navigator), 'webdriver', {
        get: () => false,
        enumerable: true,
        configurable: true
    });

    // 2. Override plugins to look like a real browser
    Object.defineProperty(navigator, 'plugins', {
        get: () => [
            {
                name: 'Chrome PDF Plugin',
                filename: 'internal-pdf-viewer',
                description: 'Portable Document Format',
                length: 1,
                item: () => null,
                namedItem: () => null
            },
            {
                name: 'Chrome PDF Viewer',
                filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai',
                description: '',
                length: 1,
                item: () => null,
                namedItem: () => null
            },
            {
                name: 'Native Client',
                filename: 'internal-nacl-plugin',
                description: '',
                length: 2,
                item: () => null,
                namedItem: () => null
            }
        ]
    });

    // 3. Override languages to match Swiss locale
    Object.defineProperty(navigator, 'languages', {
        get: () => ['de-CH', 'de', 'en-US', 'en']
    });

    // 4. Add realistic hardware concurrency
    Object.defineProperty(navigator, 'hardwareConcurrency', {
        get: () => 8
    });

    // 5. Override deviceMemory (if exists)
    if ('deviceMemory' in navigator) {
        Object.defineProperty(navigator, 'deviceMemory', {
            get: () => 8
        });
    }

    // 6. Override permissions to behave naturally
    const originalQuery = window.navigator.permissions.query;
    window.navigator.permissions.query = (parameters) => (
        parameters.name === 'notifications' ?
            Promise.resolve({ state: Notification.permission }) :
            originalQuery(parameters)
    );

    // 7. Mock battery API (if exists)
    if ('getBattery' in navigator) {
        Object.defineProperty(navigator, 'getBattery', {
            get: () => async () => ({
                charging: true,
                chargingTime: 0,
                dischargingTime: Infinity,
                level: 1.0,
                addEventListener: () => { },
                removeEventListener: () => { },
                dispatchEvent: () => true
            })
        });
    }

    // 8. Override chrome runtime (common detection vector)
    if (window.chrome) {
        // Don't completely remove chrome, just make it look normal
        Object.defineProperty(window.chrome, 'runtime', {
            get: () => ({
                OnInstalledReason: {
                    CHROME_UPDATE: "chrome_update",
                    INSTALL: "install",
                    SHARED_MODULE_UPDATE: "shared_module_update",
                    UPDATE: "update"
                },
                OnRestartRequiredReason: {
                    APP_UPDATE: "app_update",
                    OS_UPDATE: "os_update",
                    PERIODIC: "periodic"
                },
                PlatformArch: {
                    ARM: "arm",
                    ARM64: "arm64",
                    MIPS: "mips",
                    MIPS64: "mips64",
                    X86_32: "x86-32",
                    X86_64: "x86-64"
                },
                PlatformNaclArch: {
                    ARM: "arm",
                    MIPS: "mips",
                    MIPS64: "mips64",
                    X86_32: "x86-32",
                    X86_64: "x86-64"
                },
                PlatformOs: {
                    ANDROID: "android",
                    CROS: "cros",
                    LINUX: "linux",
                    MAC: "mac",
                    OPENBSD: "openbsd",
                    WIN: "win"
                },
                RequestUpdateCheckStatus: {
                    NO_UPDATE: "no_update",
                    THROTTLED: "throttled",
                    UPDATE_AVAILABLE: "update_available"
                }
            }),
            configurable: true
        });
    }

    // 9. Make iframe.contentWindow consistent
    try {
        const originalIframeGetter = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow').get;
        Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
            get: function () {
                const win = originalIframeGetter.call(this);
                if (win) {
                    try {
                        win.navigator.webdriver = undefined;
                    } catch (e) {
                        // Cross-origin iframe, ignore
                    }
                }
                return win;
            }
        });
    } catch (e) {
        // Ignore if we can't override
    }

    // 10. Randomize canvas fingerprint slightly
    const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function (type) {
        // Add tiny random noise to canvas (not detectable visually)
        const context = this.getContext('2d');
        if (context) {
            const imageData = context.getImageData(0, 0, this.width, this.height);
            for (let i = 0; i < imageData.data.length; i += 4) {
                // Add very small random noise (< 1%)
                if (secureRandom() < 0.001) {
                    imageData.data[i] = Math.min(255, imageData.data[i] + Math.floor(secureRandom() * 3) - 1);
                }
            }
            context.putImageData(imageData, 0, 0);
        }
        return originalToDataURL.apply(this, arguments);
    };

    // 11. Override toString to hide our modifications
    const originalToString = Function.prototype.toString;
    Function.prototype.toString = function () {
        // Make our overrides look like native code
        if (this === navigator.permissions.query) {
            return 'function query() { [native code] }';
        }
        if (this === HTMLCanvasElement.prototype.toDataURL) {
            return 'function toDataURL() { [native code] }';
        }
        return originalToString.call(this);
    };

    // 12. Add realistic screen properties
    if (screen.width === 0 || screen.height === 0) {
        Object.defineProperty(screen, 'width', {
            get: () => 1920
        });
        Object.defineProperty(screen, 'height', {
            get: () => 1080
        });
        Object.defineProperty(screen, 'availWidth', {
            get: () => 1920
        });
        Object.defineProperty(screen, 'availHeight', {
            get: () => 1040
        });
    }

    // 13. Override connection API if exists
    if ('connection' in navigator) {
        Object.defineProperty(navigator.connection, 'rtt', {
            get: () => 50 + Math.floor(secureRandom() * 50) // 50-100ms
        });
    }

    // 14. Hide automation controller property (Chrome DevTools Protocol)
    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
    delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;

    // 15. Override Notification.permission
    try {
        if (window.Notification) {
            Object.defineProperty(Notification, 'permission', {
                get: () => 'default'
            });
        }
    } catch (e) { }

    // 16. Add performance timing
    if (window.performance && window.performance.timing) {
        // Make timing look natural
        const now = Date.now();
        Object.defineProperty(window.performance.timing, 'navigationStart', {
            get: () => now - Math.floor(secureRandom() * 1000)
        });
    }

    // 17. Override Object.getOwnPropertyDescriptor to hide modifications
    const originalGetOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
    Object.getOwnPropertyDescriptor = function (obj, prop) {
        if (obj === Navigator.prototype && prop === 'webdriver') {
            return undefined;
        }
        return originalGetOwnPropertyDescriptor(obj, prop);
    };

    // 18. Prevent detection via Error.stack
    const originalError = Error;
    Error = function (...args) {
        const error = new originalError(...args);

        if (typeof error?.stack === "string") {
            // optional: DoS-Guard
            if (error.stack.length <= 200_000) {
                const re = /^[ \t]{0,20}at __puppeteer_evaluation_script__:\d{1,10}:\d{1,10}\r?$/gm;
                error.stack = error.stack.replace(re, "");
                // optional: leere Zeilen zusammenziehen
                error.stack = error.stack.replace(/\n{3,}/g, "\n\n");
            }
        }

        return error;
    };
    Error.prototype = originalError.prototype;

    console.log('🛡️ Stealth mode activated - bot detection evasion enabled');

})();
