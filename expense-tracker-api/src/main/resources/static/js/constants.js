export const API_BASE = '/api/v1';

export const API = Object.freeze(
    {
        REGISTER: `${API_BASE}/auth/register`,
        LOGIN: `${API_BASE}/auth/login`,
        ME: `${API_BASE}/users/me`,
        PASSWORD: `${API_BASE}/users/me/password`,
        CATEGORIES: `${API_BASE}/categories`,
        TRANSACTIONS: `${API_BASE}/transactions`,
        DASHBOARD: `${API_BASE}/transactions/dashboard`,
        BUDGETS: `${API_BASE}/budgets`,
        ALERTS: `${API_BASE}/budgets/alerts`
    }
);
export const ROUTES = Object.freeze(
    {
        LOGIN: '/login',
        REGISTER: '/register',
        DASHBOARD: '/dashboard',
        TRANSACTIONS: '/transactions',
        CATEGORIES: '/categories',
        BUDGETS: '/budgets',
        PROFILE: '/profile'
    }
);
export const HTTP_METHODS = Object.freeze({GET: 'GET', POST: 'POST', PUT: 'PUT', DELETE: 'DELETE'});
export const TYPES = Object.freeze({EXPENSE: 'CHI', INCOME: 'THU'});
export const PERIOD_TYPES = Object.freeze({MONTHLY: 'MONTHLY', WEEKLY: 'WEEKLY'});
export const STORAGE = Object.freeze(
    {
        TOKEN: 'expense-tracker.access-token',
        NOTICE: 'expense-tracker.notice'
    }
);
export const EVENTS = Object.freeze({AUTH_CHANGED: 'expense-tracker:auth-changed'});
export const AUTH_SCHEME = 'Bearer';
export const PAGE_SIZE = 10;
export const MAX_PAGE_SIZE = 100;
export const PAGE_SIZE_OPTIONS = Object.freeze(
    [PAGE_SIZE, 25, 50, MAX_PAGE_SIZE].map(
        (size) => {
            const option = [String(size), `${size} / trang`];
            return Object.freeze(option);
        }
    )
);
export const LIMITS = Object.freeze(
    {
        NAME: 100,
        EMAIL: 255,
        PASSWORD_MIN: 8,
        PASSWORD_BYTES: 72,
        NOTE: 1000
    }
);
export const MESSAGES = Object.freeze(
    {
        EXPIRED: 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.',
        FORBIDDEN: 'Không có quyền hoặc dữ liệu không tồn tại.',
        INVALID: 'Vui lòng kiểm tra lại dữ liệu đã nhập.',
        SERVER: 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.',
        NETWORK: 'Không thể kết nối tới máy chủ. Hãy kiểm tra kết nối và thử lại.',
        LOGIN: 'Email hoặc mật khẩu không đúng.',
        STORAGE: 'Trình duyệt đang chặn sessionStorage. Hãy cho phép lưu trữ cho trang này để đăng nhập.'
    }
);
