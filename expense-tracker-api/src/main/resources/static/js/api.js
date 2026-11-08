import {API, API_BASE, AUTH_SCHEME, HTTP_METHODS, MESSAGES} from './constants.js';
import {expireSession, getToken} from './auth.js';

const EXACT_FIELDS = new Set(['amount', 'limitAmount', 'totalIncome', 'totalExpense',
    'balance', 'spent', 'remaining', 'exceededAmount', 'id', 'categoryId']);
const JSON_TOKENS = /"(?:[^"\\]|\\.)*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|[^\s]/g;
const NUMBER_TOKEN = /^-?\d/;
const STATUS = Object.freeze({BAD_REQUEST: 400, UNAUTHORIZED: 401, FORBIDDEN: 403, CONFLICT: 409});

export class ApiError extends Error {
    constructor(message, details = {}) {
        super(message);
        this.name = 'ApiError';
        this.status = details.status;
        this.fieldErrors = details.fieldErrors || [];
    }
}

export function parseResponse(text) {
    let previous = '';
    let exactValue = false;
    const protectedJson = text.replace(JSON_TOKENS, (token) => {
        if (token === ':') {
            exactValue = previous.startsWith('"') && EXACT_FIELDS.has(JSON.parse(previous));
        } else if (NUMBER_TOKEN.test(token) && exactValue) {
            exactValue = false;
            previous = token;
            return JSON.stringify(token);
        } else {
            exactValue = false;
        }
        previous = token;
        return token;
    });
    const data = JSON.parse(protectedJson);
    return data;
}

function requestUrl(path, query = {}) {
    if (!path.startsWith(`${API_BASE}/`)) {
        throw new ApiError(MESSAGES.SERVER);
    }
    const url = new URL(path, window.location.origin);
    for (const [key, value] of Object.entries(query)) {
        if (value !== '' && value !== undefined && value !== null) {
            const values = Array.isArray(value) ? value : [value];
            for (const entry of values) {
                url.searchParams.append(key, String(entry));
            }
        }
    }
    return url;
}

function requestOptions(options, token) {
    const headers = {Accept: 'application/json'};
    if (token) {
        headers.Authorization = `${AUTH_SCHEME} ${token}`;
    }
    if (options.body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    const configuration = {method: options.method || HTTP_METHODS.GET, headers,
        signal: options.signal, credentials: 'omit', cache: 'no-store'};
    if (options.body !== undefined) {
        configuration.body = JSON.stringify(options.body);
    }
    return configuration;
}

function errorMessage(status, path) {
    if (status === STATUS.UNAUTHORIZED) {
        return path === API.LOGIN ? MESSAGES.LOGIN : MESSAGES.EXPIRED;
    }
    if (status === STATUS.FORBIDDEN) {
        return MESSAGES.FORBIDDEN;
    }
    if (status === STATUS.BAD_REQUEST) {
        return MESSAGES.INVALID;
    }
    if (status === STATUS.CONFLICT) {
        return conflictMessage(path);
    }
    return MESSAGES.SERVER;
}

function conflictMessage(path) {
    if (path === API.REGISTER) {
        return 'Email này đã được sử dụng. Hãy đăng nhập hoặc dùng email khác.';
    }
    if (path === API.BUDGETS) {
        return 'Danh mục đã có ngân sách cho kỳ và loại này. Hãy sửa hạn mức ngân sách hiện có.';
    }
    return 'Dữ liệu đang được sử dụng. Hãy kiểm tra các giao dịch và ngân sách liên quan.';
}

async function readResponse(response) {
    const text = await response.text();
    if (!text) {
        return null;
    }
    try {
        return parseResponse(text);
    } catch {
        throw new ApiError(MESSAGES.SERVER, {status: response.status});
    }
}

export async function request(path, options = {}) {
    const token = options.auth === false ? null : getToken();
    let response;
    try {
        response = await fetch(requestUrl(path, options.query), requestOptions(options, token));
    } catch (error) {
        if (error.name === 'AbortError') {
            throw error;
        }
        throw new ApiError(MESSAGES.NETWORK);
    }
    if (response.status === STATUS.UNAUTHORIZED && options.auth !== false) {
        expireSession(token);
    }
    const data = await readResponse(response);
    if (!response.ok) {
        const fieldErrors = response.status === STATUS.BAD_REQUEST ? data?.fieldErrors : [];
        throw new ApiError(errorMessage(response.status, path), {status: response.status, fieldErrors});
    }
    return data;
}
