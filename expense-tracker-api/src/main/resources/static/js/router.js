import {ROUTES} from './constants.js';
import {getToken} from './auth.js';
import {button, el, notice} from './dom.js';
import {createShell} from './shell.js';
import {render as login} from './pages/login.js';
import {render as register} from './pages/register.js';
import {render as dashboard} from './pages/dashboard.js';
import {render as transactions} from './pages/transactions.js';
import {render as categories} from './pages/categories.js';
import {render as budgets} from './pages/budgets.js';
import {render as profile} from './pages/profile.js';

const PAGES = new Map(
    [
        [
            ROUTES.LOGIN,
            login
        ],
        [
            ROUTES.REGISTER,
            register
        ],
        [
            ROUTES.DASHBOARD,
            dashboard
        ],
        [
            ROUTES.TRANSACTIONS,
            transactions
        ],
        [
            ROUTES.CATEGORIES,
            categories
        ],
        [
            ROUTES.BUDGETS,
            budgets
        ],
        [
            ROUTES.PROFILE,
            profile
        ]
    ]
);
const PUBLIC = new Set([ROUTES.LOGIN, ROUTES.REGISTER]);
let activeRequest;
let revision = 0;

export function startRouter(root) {
    const navigate = () => navigateToRoute(root);
    window.addEventListener('hashchange', navigate);
    navigate();
}

async function navigateToRoute(root) {
    activeRequest?.abort();
    activeRequest = new AbortController();
    const currentRevision = ++revision;
    const path = window.location.hash.slice(1) || ROUTES.DASHBOARD;
    if (redirectGuard(path)) {
        return;
    }
    const container = prepareContainer(root, path);
    const context = {
        container,
        signal: activeRequest.signal,
        isCurrent: () => currentRevision
        === revision
    };
    try {
        const render = PAGES.get(path);
        if (!render) {
            showUnknownRoute(container);
            return;
        }
        await render(context);
        if (context.isCurrent()) {
            container.querySelector('h1')?.focus();
        }
    } catch (error) {
        if (context.isCurrent() && error.name !== 'AbortError') {
            container.append(
                notice(
                    error.message,
                    'error'
                ),
                button(
                    'Thử lại',
                    () => navigateToRoute(
                        root
                    )
                )
            );
        }
    }
}

function redirectGuard(path) {
    const token = getToken();
    if (!token && !PUBLIC.has(path)) {
        window.location.replace(`#${ROUTES.LOGIN}`);
        return true;
    }
    if (token && PUBLIC.has(path)) {
        window.location.replace(`#${ROUTES.DASHBOARD}`);
        return true;
    }
    return false;
}

function prepareContainer(root, path) {
    if (!PUBLIC.has(path)) {
        return createShell(root, path);
    }
    const container = el('main', {id: 'main-content', className: 'auth-container', tabIndex: -1});
    root.replaceChildren(container);
    return container;
}

function showUnknownRoute(container) {
    container.append(
        el('h1', {text: 'Không tìm thấy trang', tabIndex: -1}),
        el('p', {text: 'Đường dẫn này không có trong sổ chi tiêu của bạn.'}),
        el('a', {href: `#${ROUTES.DASHBOARD}`, className: 'button', text: 'Về tổng quan'})
    );
}
