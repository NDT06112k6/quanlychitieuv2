import { API, HTTP_METHODS, LIMITS } from '../constants.js';
import { request } from '../api.js';
import { clear, el, field, notice, pageHeader } from '../dom.js';
import { createForm, isActive } from '../ui.js';
import { requirePassword, requireText } from '../validation.js';

const CURRENT_PASSWORD_MINIMUM_LENGTH = 1;
const ADMIN_ROLE = 'ADMIN';

export async function render(context) {
    context.container.append(pageHeader('Hồ sơ cá nhân', 'Thông tin tài khoản và bảo mật của bạn.'));
    const content = el('div', { className: 'profile-grid' }, [notice('Đang tải hồ sơ…')]);
    context.container.append(content);
    try {
        const profile = await request(API.ME, { signal: context.signal });
        if (!isActive(context)) {
            return;
        }
        content.replaceChildren(profilePanel(context, profile), passwordPanel(context));
    } catch (error) {
        if (isActive(context) && error.name !== 'AbortError') {
            content.replaceChildren(notice(error.message, 'error'));
        }
    }
}

function profilePanel(context, profile) {
    const displayName = field('displayName', 'Tên hiển thị', {
        value: profile.displayName,
        required: true,
        maxLength: LIMITS.NAME,
        autocomplete: 'name'
    });
    const feedback = el('div', { attrs: { 'aria-live': 'polite' } });
    const form = profileForm(context, displayName, feedback);
    const panel = el('section', { className: 'panel' }, [
        el('h2', { text: 'Thông tin tài khoản' }),
        accountDetails(profile),
        form.element,
        feedback
    ]);
    return panel;
}

function accountDetails(profile) {
    const details = el('dl', { className: 'account-details' }, [
        el('dt', { text: 'Email' }),
        el('dd', { text: profile.email }),
        el('dt', { text: 'Vai trò' }),
        el('dd', { text: profile.role === ADMIN_ROLE ? 'Quản trị viên' : 'Người dùng' })
    ]);
    return details;
}

function profileForm(context, displayName, feedback) {
    const form = createForm({
        fields: [displayName],
        submitLabel: 'Lưu thông tin',
        onSubmit: async (values) => {
            clear(feedback);
            const body = { displayName: requireText(values.displayName, 'displayName', LIMITS.NAME) };
            const profile = await request(API.ME, { method: HTTP_METHODS.PUT, body, signal: context.signal });
            if (isActive(context)) {
                displayName.input.value = profile.displayName;
                feedback.append(notice('Đã cập nhật tên hiển thị.', 'success'));
            }
        }
    });
    return form;
}

function passwordPanel(context) {
    const currentPassword = passwordField('currentPassword', 'Mật khẩu hiện tại', 'current-password');
    const newPassword = passwordField('newPassword', 'Mật khẩu mới', 'new-password');
    const feedback = el('div', { attrs: { 'aria-live': 'polite' } });
    const form = passwordForm(context, [currentPassword, newPassword], feedback);
    const panel = el('section', { className: 'panel' }, [
        el('h2', { text: 'Đổi mật khẩu' }),
        el('p', { className: 'muted', text: 'Mật khẩu mới có ít nhất 8 ký tự và không vượt quá 72 byte UTF-8.' }),
        form.element,
        feedback
    ]);
    return panel;
}

function passwordField(name, label, autocomplete) {
    const password = field(name, label, {
        type: 'password',
        required: true,
        maxLength: LIMITS.PASSWORD_BYTES,
        autocomplete
    });
    return password;
}

function passwordForm(context, fields, feedback) {
    const form = createForm({
        fields,
        submitLabel: 'Đổi mật khẩu',
        onSubmit: async (values) => {
            clear(feedback);
            const body = passwordRequest(values);
            await request(API.PASSWORD, { method: HTTP_METHODS.PUT, body, signal: context.signal });
            if (!isActive(context)) {
                return;
            }
            for (const password of fields) {
                password.input.value = '';
            }
            feedback.append(notice('Mật khẩu đã đổi. Bạn có thể đăng xuất rồi đăng nhập lại bằng mật khẩu mới.', 'success'));
        }
    });
    return form;
}

function passwordRequest(values) {
    const currentPassword = requirePassword(values.currentPassword, 'currentPassword', CURRENT_PASSWORD_MINIMUM_LENGTH);
    const newPassword = requirePassword(values.newPassword, 'newPassword', LIMITS.PASSWORD_MIN);
    const body = { currentPassword, newPassword };
    return body;
}

