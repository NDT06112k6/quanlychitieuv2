import assert from 'node:assert/strict';
import {beforeEach, test} from 'node:test';
import {API, HTTP_METHODS, MESSAGES, ROUTES, STORAGE} from '../../main/resources/static/js/constants.js';
import {ApiError, request} from '../../main/resources/static/js/api.js';
import {clearSession, expireSession, getToken, logout, saveSession, takeNotice} from '../../main/resources/static/js/auth.js';

class MemoryStorage {
    values = new Map();

    getItem(key) {
        return this.values.get(key) ?? null;
    }

    setItem(key, value) {
        this.values.set(key, String(value));
    }

    removeItem(key) {
        this.values.delete(key);
    }
}

beforeEach(() => {
    globalThis.sessionStorage = new MemoryStorage();
    globalThis.window = {location: {origin: 'http://localhost:8080', hash: '#/dashboard'}, dispatchEvent() {}};
    globalThis.fetch = async () => new Response('{}');
});

function setToken(token = 'synthetic-test-token') {
    saveSession({accessToken: token, tokenType: 'Bearer', expiresInSeconds: 3600});
}

function errorResponse(status, details = {}) {
    return new Response(JSON.stringify({status, message: 'SQL stack trace: private technical detail', ...details}),
        {status, headers: {'Content-Type': 'application/json'}});
}

test('protected request sends Bearer from sessionStorage and decimal strings unchanged', async () => {
    setToken();
    let captured;
    globalThis.fetch = async (url, options) => {
        captured = {url, options};
        return new Response('{"id":1,"amount":9999999999999.99}', {status: 201});
    };
    const result = await request(API.TRANSACTIONS, {method: HTTP_METHODS.POST,
        body: {categoryId: '9223372036854775807', amount: '9999999999999.99'}, query: {page: 0, size: 10}});
    assert.equal(captured.options.headers.Authorization, 'Bearer synthetic-test-token');
    assert.equal(captured.options.credentials, 'omit');
    assert.equal(captured.options.cache, 'no-store');
    assert.equal(JSON.parse(captured.options.body).amount, '9999999999999.99');
    assert.equal(result.amount, '9999999999999.99');
    assert.equal(captured.url.searchParams.get('page'), '0');
});

test('public login omits Bearer even when a session token exists', async () => {
    setToken();
    let authorization;
    globalThis.fetch = async (url, options) => {
        authorization = options.headers.Authorization;
        return new Response('{"accessToken":"new-test-token","tokenType":"Bearer"}');
    };
    await request(API.LOGIN, {auth: false, method: HTTP_METHODS.POST, body: {email: 'test@example.invalid', password: 'test-only'}});
    assert.equal(authorization, undefined);
});

test('query encoder preserves repeated sort and omits empty filters', async () => {
    let captured;
    globalThis.fetch = async (url) => {
        captured = url;
        return new Response('{}');
    };
    await request(API.TRANSACTIONS, {query: {sort: ['amount,asc', 'occurredOn,desc'], categoryId: '', type: undefined}});
    assert.deepEqual(captured.searchParams.getAll('sort'), ['amount,asc', 'occurredOn,desc']);
    assert.equal(captured.searchParams.has('categoryId'), false);
    assert.equal(captured.searchParams.has('type'), false);
});

test('401 clears the matching token and redirects with a one-time expired-session notice', async () => {
    setToken();
    globalThis.fetch = async () => errorResponse(401);
    await assert.rejects(request(API.TRANSACTIONS), (error) => error.status === 401 && error.message === MESSAGES.EXPIRED);
    assert.equal(getToken(), null);
    assert.equal(window.location.hash, `#${ROUTES.LOGIN}`);
    assert.equal(takeNotice(), MESSAGES.EXPIRED);
    assert.equal(takeNotice(), null);
});

test('a stale request 401 cannot erase a newer login token', async () => {
    setToken('old-test-token');
    let complete;
    globalThis.fetch = () => new Promise((resolve) => {
        complete = resolve;
    });
    const pending = request(API.TRANSACTIONS);
    setToken('new-test-token');
    complete(errorResponse(401));
    await assert.rejects(pending, ApiError);
    assert.equal(getToken(), 'new-test-token');
    assert.equal(window.location.hash, '#/dashboard');
    assert.equal(takeNotice(), null);
});

test('incorrect public login returns generic credentials error without expiring an existing token', async () => {
    setToken();
    globalThis.fetch = async () => errorResponse(401);
    await assert.rejects(request(API.LOGIN, {auth: false}), (error) => error.message === MESSAGES.LOGIN);
    assert.equal(getToken(), 'synthetic-test-token');
    assert.equal(takeNotice(), null);
});

test('400 retains structured field errors for form rendering', async () => {
    const fieldErrors = [{field: 'displayName', message: 'must not be blank'}];
    globalThis.fetch = async () => errorResponse(400, {fieldErrors});
    await assert.rejects(request(API.ME), (error) => {
        assert.equal(error.message, MESSAGES.INVALID);
        assert.deepEqual(error.fieldErrors, fieldErrors);
        return true;
    });
});

for (const [status, path, expected] of [
    [403, API.TRANSACTIONS, MESSAGES.FORBIDDEN],
    [409, API.BUDGETS, 'Danh mục đã có ngân sách'],
    [409, API.REGISTER, 'Email này đã được sử dụng'],
    [409, `${API.CATEGORIES}/1`, 'Dữ liệu đang được sử dụng'],
    [500, API.ME, MESSAGES.SERVER]
]) {
    test(`${status} ${path} returns a friendly error without exposing server details`, async () => {
        globalThis.fetch = async () => errorResponse(status);
        await assert.rejects(request(path), (error) => {
            assert.equal(error.status, status);
            assert.ok(error.message.includes(expected));
            assert.equal(error.message.includes('SQL'), false);
            assert.deepEqual(error.fieldErrors, []);
            return true;
        });
    });
}

test('204 resolves without attempting to parse a JSON body', async () => {
    globalThis.fetch = async () => new Response(null, {status: 204});
    assert.equal(await request(`${API.BUDGETS}/1`, {method: HTTP_METHODS.DELETE}), null);
});

test('network failure and malformed JSON produce sanitized messages', async () => {
    globalThis.fetch = async () => {
        throw new Error('private network detail');
    };
    await assert.rejects(request(API.ME), (error) => error.message === MESSAGES.NETWORK);
    globalThis.fetch = async () => new Response('<html>internal stack trace</html>', {status: 500});
    await assert.rejects(request(API.ME), (error) => error.message === MESSAGES.SERVER);
});

test('aborted requests preserve AbortError for page lifecycle cancellation', async () => {
    const abort = new DOMException('synthetic cancellation', 'AbortError');
    globalThis.fetch = async () => {
        throw abort;
    };
    await assert.rejects(request(API.ME), (error) => error === abort);
});

test('logout removes only this application token and persists a single-use notice', () => {
    sessionStorage.setItem('other-app', 'keep');
    setToken();
    logout();
    assert.equal(getToken(), null);
    assert.equal(sessionStorage.getItem('other-app'), 'keep');
    assert.equal(window.location.hash, `#${ROUTES.LOGIN}`);
    assert.match(takeNotice(), /đăng xuất/u);
});

test('authentication fails visibly when browser storage is blocked', () => {
    globalThis.sessionStorage = {getItem() { throw new Error('blocked'); },
        setItem() { throw new Error('blocked'); }, removeItem() { throw new Error('blocked'); }};
    assert.equal(getToken(), null);
    assert.throws(() => setToken(), (error) => error.message === MESSAGES.STORAGE);
    assert.doesNotThrow(() => clearSession());
});

test('a malformed auth response never writes a token', () => {
    assert.throws(() => saveSession({accessToken: 'synthetic-token', tokenType: 'Basic'}));
    assert.equal(sessionStorage.getItem(STORAGE.TOKEN), null);
    expireSession('unrelated-old-token');
    assert.equal(takeNotice(), null);
});
