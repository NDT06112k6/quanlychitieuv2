import {ROUTES} from './constants.js';
import {logout} from './auth.js';
import {button, el} from './dom.js';

const NAVIGATION = [[ROUTES.DASHBOARD, '◫', 'Tổng quan'], [ROUTES.TRANSACTIONS, '↗', 'Giao dịch'],
    [ROUTES.CATEGORIES, '▦', 'Danh mục'], [ROUTES.BUDGETS, '◎', 'Ngân sách'], [ROUTES.PROFILE, '○', 'Hồ sơ']];

export function createShell(root, path) {
    const navigation = el('nav', {className: 'navigation', attrs: {'aria-label': 'Điều hướng chính'}});
    for (const [route, icon, label] of NAVIGATION) {
        const link = el('a', {href: `#${route}`, className: route === path ? 'nav-link active' : 'nav-link'}, [
            el('span', {text: icon, attrs: {'aria-hidden': 'true'}}), el('span', {text: label})]);
        if (route === path) {
            link.setAttribute('aria-current', 'page');
        }
        navigation.append(link);
    }
    const sidebar = el('aside', {className: 'sidebar'}, [brand(), navigation,
        el('div', {className: 'sidebar-footer'}, [el('p', {text: 'Thói quen nhỏ. Tương lai lớn.'}),
            button('Đăng xuất ↪', logout, 'button button-logout')])]);
    const content = el('main', {className: 'workspace', id: 'main-content', tabIndex: -1});
    root.replaceChildren(el('div', {className: 'app-shell'}, [sidebar, content]));
    return content;
}

function brand() {
    return el('a', {href: `#${ROUTES.DASHBOARD}`, className: 'brand'}, [
        el('span', {className: 'brand-mark', text: 'S', attrs: {'aria-hidden': 'true'}}),
        el('span', {}, [el('strong', {text: 'Sổ chi tiêu'}), el('small', {text: 'Tài chính trong tầm tay'})])]);
}
