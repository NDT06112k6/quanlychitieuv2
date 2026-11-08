import {AUTH_SCHEME, EVENTS, MESSAGES, ROUTES, STORAGE} from './constants.js';

export function getToken() {
    try {
        const token = sessionStorage.getItem(STORAGE.TOKEN);
        return token;
    } catch {
        return null;
    }
}

export function saveSession(response) {
    if (!response.accessToken || response.tokenType !== AUTH_SCHEME) {
        throw new Error(MESSAGES.SERVER);
    }
    try {
        sessionStorage.setItem(STORAGE.TOKEN, response.accessToken);
    } catch {
        throw new Error(MESSAGES.STORAGE);
    }
    window.dispatchEvent(new Event(EVENTS.AUTH_CHANGED));
}

export function clearSession() {
    try {
        sessionStorage.removeItem(STORAGE.TOKEN);
    } catch {
        // A blocked store is already treated as an unauthenticated session.
    }
    window.dispatchEvent(new Event(EVENTS.AUTH_CHANGED));
}

export function redirectToLogin(message) {
    clearSession();
    try {
        sessionStorage.setItem(STORAGE.NOTICE, message);
    } catch {
        // The current page can still show the API error when storage is blocked.
    }
    window.location.hash = `#${ROUTES.LOGIN}`;
}

export function expireSession(requestToken) {
    if (getToken() === requestToken) {
        redirectToLogin(MESSAGES.EXPIRED);
    }
}

export function takeNotice() {
    try {
        const message = sessionStorage.getItem(STORAGE.NOTICE);
        sessionStorage.removeItem(STORAGE.NOTICE);
        return message;
    } catch {
        return null;
    }
}

export function logout() {
    redirectToLogin('Bạn đã đăng xuất an toàn.');
}
