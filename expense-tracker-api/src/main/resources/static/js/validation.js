import {LIMITS, PERIOD_TYPES} from './constants.js';

export class ValidationError extends Error {
    constructor(field, message) {
        super('Vui lòng kiểm tra lại dữ liệu đã nhập.');
        this.fieldErrors = [{field, message}];
    }
}

export function requireText(value, field, maximum = LIMITS.NAME) {
    const text = String(value ?? '').trim();
    if (!text || text.length > maximum) {
        throw new ValidationError(field, `Vui lòng nhập từ 1 đến ${maximum} ký tự.`);
    }
    return text;
}

export function requireEmail(value) {
    const email = requireText(value, 'email', LIMITS.EMAIL).toLowerCase();
    if (!/^[^\s@]+@[^\s@]+$/.test(email) || email.split('@')[0].length > 64) {
        throw new ValidationError('email', 'Địa chỉ email không hợp lệ.');
    }
    return email;
}

export function requirePassword(value, field, minimum = LIMITS.PASSWORD_MIN) {
    const password = String(value ?? '');
    const bytes = new TextEncoder().encode(password).length;
    if (!password.trim() || password.length < minimum || bytes > LIMITS.PASSWORD_BYTES) {
        throw new ValidationError(
            field,
            `Mật khẩu cần ít nhất ${minimum} ký tự và không quá 72 byte UTF-8.`
        );
    }
    return password;
}

export function moneyValue(value, field = 'amount') {
    const text = String(value ?? '').trim().replace(',', '.');
    if (!/^\d+(?:\.\d{1,2})?$/.test(text)) {
        throw new ValidationError(
            field,
            'Nhập số tiền dương, tối đa 2 chữ số thập phân; không dùng dấu phân cách hàng nghìn.'
        );
    }
    const [whole, fraction = ''] = text.split('.');
    const integer = whole.replace(/^0+(?=\d)/, '');
    const normalized = `${integer}.${fraction.padEnd(2, '0')}`;
    if (integer.length > 13 || BigInt(integer + fraction.padEnd(2, '0')) <= 0n) {
        throw new ValidationError(
            field,
            'Số tiền phải lớn hơn 0 và không vượt 9.999.999.999.999,99.'
        );
    }
    return normalized;
}

export function requireDate(value, field = 'occurredOn') {
    const text = String(value ?? '');
    const date = new Date(`${text}T00:00:00Z`);
    if (!/^[1-9]\d{3}-\d{2}-\d{2}$/.test(text) || Number.isNaN(date.getTime())
        || date.toISOString().slice(0, 10) !== text) {
        throw new ValidationError(field, 'Chọn ngày hợp lệ từ 01/01/1000 đến 31/12/9999.');
    }
    return text;
}

export function requireRange(start, end) {
    if (start) {
        requireDate(start, 'startDate');
    }
    if (end) {
        requireDate(end, 'endDate');
    }
    if (start && end && start > end) {
        throw new ValidationError('endDate', 'Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.');
    }
}

export function requirePeriod(type, value) {
    const period = String(value ?? '').trim();
    const monthValid = /^[1-9]\d{3}-(0[1-9]|1[0-2])$/.test(period);
    if (type === PERIOD_TYPES.MONTHLY && monthValid) {
        return period;
    }
    if (type === PERIOD_TYPES.WEEKLY && validWeek(period)) {
        return period;
    }
    throw new ValidationError(
        'period',
        'Nhập kỳ tháng YYYY-MM hoặc tuần ISO YYYY-Www đúng với loại đã chọn.'
    );
}

function validWeek(period) {
    const match = /^([1-9]\d{3})-W(0[1-9]|[1-4]\d|5[0-3])$/.exec(period);
    if (!match) {
        return false;
    }
    const year = Number(match[1]);
    const week = Number(match[2]);
    const reference = new Date(Date.UTC(year, 0, 4));
    const weekday = reference.getUTCDay() || 7;
    reference.setUTCDate(reference.getUTCDate() - weekday + 1 + (week - 1) * 7 + 3);
    return reference.getUTCFullYear() === year;
}
