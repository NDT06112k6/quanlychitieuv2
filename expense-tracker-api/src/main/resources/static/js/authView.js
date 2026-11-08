import {API, HTTP_METHODS, LIMITS, ROUTES} from './constants.js';
import {request} from './api.js';
import {redirectToLogin, saveSession, takeNotice} from './auth.js';
import {el, field, notice} from './dom.js';
import {createForm} from './ui.js';
import {requireEmail, requirePassword, requireText} from './validation.js';

export function renderAuth(context, register) {
    const heading = register ? 'Bắt đầu hành trình của bạn' : 'Chào mừng bạn trở lại';
    const card = el(
        'section',
        {
            className: 'auth-card'
        },
        [
            el(
                'p',
                {
                    className: 'eyebrow',
                    text: 'SỔ CHI TIÊU'
                }
            ),
            el('h1', {text: heading, tabIndex: -1}),
            el(
                'p',
                {
                    className: 'muted',
                    text: register
                    ? 'Tạo tài khoản để quản lý tài chính theo cách của bạn.'
                    : 'Đăng nhập để tiếp tục chăm sóc tài chính của bạn.'
                }
            )
        ]
    );
    const message = takeNotice();
    if (message) {
        card.append(notice(message));
    }
    card.append(authForm(context, register).element, authSwitch(register));
    context.container.append(el('div', {className: 'auth-layout'}, [authStory(), card]));
}

function authForm(context, register) {
    const fields = [
        field(
            'email',
            'Email',
            {
                type: 'email',
                autocomplete: 'username',
                required: true,
                maxLength: LIMITS.EMAIL,
                placeholder: 'ban@example.com'
            }
        )
    ];
    if (register) {
        fields.push(
            field(
                'displayName',
                'Tên hiển thị',
                {
                    required: true,
                    maxLength: LIMITS.NAME,
                    autocomplete: 'name',
                    placeholder: 'Tên bạn muốn được gọi'
                }
            )
        );
    }
    fields.push(
        field(
            'password',
            'Mật khẩu',
            {
                type: 'password',
                required: true,
                maxLength: LIMITS.PASSWORD_BYTES,
                autocomplete: register
                ? 'new-password'
                : 'current-password',
                hint: register
                ? 'Từ 8 ký tự, tối đa 72 byte UTF-8.'
                : ''
            }
        )
    );
    const form = createForm(
        {
            fields,
            submitLabel: register
            ? 'Tạo tài khoản'
            : 'Đăng nhập',
            onSubmit: async (values) => {
                await authenticate(context, {values, register});
            }
        }
    );
    form.element.classList.add('auth-form');
    return form;
}

async function authenticate(context, options) {
    const body = {
        email: requireEmail(options.values.email),
        password: requirePassword(
            options.values.password,
            'password',
            options.register
            ? LIMITS.PASSWORD_MIN
            : 1
        )
    };
    if (options.register) {
        body.displayName = requireText(options.values.displayName, 'displayName');
    }
    const path = options.register ? API.REGISTER : API.LOGIN;
    const response = await request(
        path,
        {
            method: HTTP_METHODS.POST,
            body,
            auth: false,
            signal: context.signal
        }
    );
    if (!context.isCurrent()) {
        return;
    }
    if (options.register) {
        redirectToLogin('Đăng ký thành công. Hãy đăng nhập để bắt đầu ghi chép.');
    } else {
        saveSession(response);
        window.location.hash = `#${ROUTES.DASHBOARD}`;
    }
}

function authSwitch(register) {
    const link = el(
        'a',
        {
            href: `#${register ? ROUTES.LOGIN : ROUTES.REGISTER}`,
            text: register ? 'Đăng nhập' : 'Tạo tài khoản mới'
        }
    );
    return el(
        'p',
        {
            className: 'auth-switch'
        },
        [
            register
            ? 'Đã có tài khoản? '
            : 'Chưa có tài khoản? ',
            link
        ]
    );
}

function authStory() {
    return el(
        'aside',
        {className: 'auth-story'},
        [
            el('div', {className: 'brand-mark', text: 'S'}),
            el('p', {className: 'eyebrow', text: 'GHI CHÉP HÔM NAY · AN TÂM NGÀY MAI'}),
            el('h2', {text: 'Để mỗi khoản chi đều có ý nghĩa.'}),
            el(
                'p',
                {text: 'Một nơi gọn gàng cho thu nhập, chi tiêu và những kế hoạch của riêng bạn.'}
            ),
            el(
                'div',
                {className: 'story-notebook', attrs: {'aria-hidden': 'true'}},
                [
                    el('span', {className: 'notebook-label', text: 'Kế hoạch tài chính'}),
                    el('span', {className: 'notebook-line', text: 'Thu nhập  ↗'}),
                    el('span', {className: 'notebook-line', text: 'Chi tiêu  ↘'}),
                    el('span', {className: 'notebook-line', text: 'Ngân sách  ✓'})
                ]
            ),
            el('small', {text: 'Dữ liệu của bạn, không gian của bạn.'})
        ]
    );
}
