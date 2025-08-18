// Disable WebAuthn/Passkey functionality
if (window.navigator && window.navigator.credentials) {
    console.log('Disabling WebAuthn credentials API');
    Object.defineProperty(window.navigator, 'credentials', {
        value: undefined,
        writable: false,
        configurable: false
    });
}

// Also disable the create method if it exists
if (window.PublicKeyCredential) {
    console.log('Disabling PublicKeyCredential');
    Object.defineProperty(window, 'PublicKeyCredential', {
        value: undefined,
        writable: false,
        configurable: false
    });
}

console.log('WebAuthn disabled successfully');
