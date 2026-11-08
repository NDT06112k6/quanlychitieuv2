import {el, button, clear, notice} from './dom.js';
import {MESSAGES} from './constants.js';
import {ValidationError} from './validation.js';

const FIELD_HINTS = Object.freeze(
    {
        email: 'Địa chỉ email không hợp lệ hoặc vượt 255 ký tự.',
        password: 'Kiểm tra mật khẩu: ít nhất 8 ký tự, tối đa 72 byte UTF-8.',
        currentPassword: 'Vui lòng kiểm tra mật khẩu hiện tại (tối đa 72 byte UTF-8).',
        newPassword: 'Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.',
        name: 'Tên danh mục cần từ 1 đến 100 ký tự.',
        displayName: 'Tên hiển thị cần từ 1 đến 100 ký tự.',
        categoryId: 'Vui lòng chọn danh mục của bạn.',
        amount: 'Kiểm tra số tiền dương và tối đa hai chữ số thập phân.',
        limitAmount: 'Kiểm tra hạn mức dương và tối đa hai chữ số thập phân.',
        occurredOn: 'Vui lòng chọn ngày hợp lệ.',
        startDate: 'Ngày bắt đầu không hợp lệ.',
        endDate: 'Ngày kết thúc không hợp lệ.',
        period: 'Kỳ tháng hoặc tuần không hợp lệ.',
        type: 'Vui lòng chọn loại hợp lệ.',
        note: 'Ghi chú tối đa 1.000 ký tự.'
    }
);

export function isActive(context) {
    return !context.signal.aborted && context.isCurrent();
}

export function showPageError(context, error, target) {
    if (isActive(context) && error.name !== 'AbortError') {
        target.replaceChildren(notice(error.message || MESSAGES.SERVER, 'error'));
    }
}

export function closePanelButton(panel) {
    return button(
        'Hủy',
        () => {
            panel.hidden = true;
        },
        'button button-secondary'
    );
}

export function createForm(configuration) {
    const errors = el('div', {className: 'form-feedback'});
    const fieldset = el(
        'fieldset',
        {
            className: 'field-grid'
        },
        configuration.fields.map(
            (entry) => entry.wrapper
        )
    );
    const submit = el(
        'button',
        {
            type: 'submit',
            className: 'button',
            text: configuration.submitLabel
        }
    );
    const element = el(
        'form',
        {className: 'data-form', noValidate: true},
        [
            errors,
            fieldset,
            el('div', {className: 'form-actions'}, [submit])
        ]
    );
    const resetErrors = () => resetFieldErrors(configuration.fields, errors);
    const setErrors = (error) => displayFormErrors(error, {fields: configuration.fields, errors});
    element.addEventListener(
        'submit',
        async (event) => {
            event.preventDefault();
            if (submit.disabled) {
                return;
            }
            resetErrors();
            await submitForm(configuration, {fieldset, submit, element}, setErrors);
        }
    );
    return {element, resetErrors, setErrors};
}

async function submitForm(configuration, controls, setErrors) {
    controls.fieldset.disabled = true;
    controls.submit.disabled = true;
    controls.element.setAttribute('aria-busy', 'true');
    controls.submit.textContent = 'Đang xử lý…';
    try {
        const values = readFields(configuration.fields);
        await configuration.onSubmit(values);
    } catch (error) {
        if (error.name !== 'AbortError') {
            setErrors(error);
        }
    } finally {
        controls.fieldset.disabled = false;
        controls.submit.disabled = false;
        controls.element.removeAttribute('aria-busy');
        controls.submit.textContent = configuration.submitLabel;
    }
}

function readFields(fields) {
    const values = {};
    for (const entry of fields) {
        const input = entry.input;
        if (input.validity?.badInput) {
            throw new ValidationError(
                input.name,
                'Thông tin chưa đúng định dạng. Vui lòng nhập lại.'
            );
        }
        if (input.required && !input.value.trim()) {
            throw new ValidationError(input.name, 'Vui lòng nhập hoặc chọn thông tin này.');
        }
        if (input.maxLength > 0 && input.value.length > input.maxLength) {
            throw new ValidationError(input.name, `Chỉ nhập tối đa ${input.maxLength} ký tự.`);
        }
        values[input.name] = input.value;
    }
    return values;
}

function resetFieldErrors(fields, errors) {
    clear(errors);
    for (const entry of fields) {
        entry.error.textContent = '';
        entry.input.removeAttribute('aria-invalid');
    }
}

function displayFormErrors(error, context) {
    resetFieldErrors(context.fields, context.errors);
    context.errors.append(notice(error.message || MESSAGES.SERVER, 'error'));
    let first;
    for (const issue of error.fieldErrors || []) {
        const name = issue.field.split('.').pop();
        const entry = context.fields.find(
            (candidate) => candidate.input.name
            === issue.field
            || candidate.input.name
            === name
        );
        if (entry) {
            entry.error.textContent = error.status
            ? (FIELD_HINTS[name]
                || MESSAGES.INVALID)
            : issue.message;
            entry.input.setAttribute('aria-invalid', 'true');
            first = first || entry.input;
        }
    }
    if (first) {
        first.focus();
    }
}

export function table(headers, rows) {
    const head = el(
        'tr',
        {},
        headers.map(
            (title) => el(
                'th',
                {
                    text: title,
                    attrs: {
                        scope: 'col'
                    }
                }
            )
        )
    );
    const body = el('tbody');
    for (const row of rows) {
        body.append(el('tr', {}, row.map((cell) => el('td', {}, [cell]))));
    }
    const content = el('table', {className: 'data-table'}, [el('thead', {}, [head]), body]);
    return el(
        'div',
        {
            className: 'table-wrap',
            tabIndex: 0,
            attrs: {
                'aria-label': 'Bảng dữ liệu có thể cuộn ngang'
            }
        },
        [
            content
        ]
    );
}

export function pagination(page, onPage) {
    const previous = button('← Trước', () => onPage(page.number - 1), 'button button-secondary');
    const next = button('Sau →', () => onPage(page.number + 1), 'button button-secondary');
    previous.disabled = page.number <= 0;
    next.disabled = page.number + 1 >= page.totalPages;
    const label = page.totalElements === 0
    ? 'Chưa có dữ liệu'
    :
    `Trang ${page.number + 1} / ${page.totalPages} · ${page.totalElements} bản ghi`;
    return el(
        'nav',
        {className: 'pagination', attrs: {'aria-label': 'Phân trang'}},
        [
            el(
                'span',
                {
                    className: 'muted',
                    text: label
                }
            ),
            el(
                'div',
                {
                    className: 'action-row'
                },
                [
                    previous,
                    next
                ]
            )
        ]
    );
}
