// Disable WebAuthn by removing credentials API and PublicKeyCredential
if (window.navigator && window.navigator.credentials) {
    Object.defineProperty(window.navigator, 'credentials', {
        value: undefined,
        writable: false,
        configurable: false
    });
}

if (window.PublicKeyCredential) {
    Object.defineProperty(window, 'PublicKeyCredential', {
        value: undefined,
        writable: false,
        configurable: false
    });
}
