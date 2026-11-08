const CURRENCY = new Intl.NumberFormat('vi-VN', {style: 'currency', currency: 'VND',
    minimumFractionDigits: 2, maximumFractionDigits: 2});
const DECIMAL = /^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/;
const CENTS_SCALE = 2;

export function decimalCents(value) {
    const match = DECIMAL.exec(String(value));
    if (!match) {
        throw new Error('Số tiền máy chủ trả về không hợp lệ.');
    }
    const fraction = match[3] || '';
    const exponent = Number(match[4] || 0);
    const scale = fraction.length - exponent;
    if (Math.abs(scale) > 100) {
        throw new Error('Số tiền nằm ngoài phạm vi hiển thị.');
    }
    let digits = BigInt(match[2] + fraction);
    if (scale <= CENTS_SCALE) {
        digits *= 10n ** BigInt(CENTS_SCALE - scale);
    } else {
        const divisor = 10n ** BigInt(scale - CENTS_SCALE);
        if (digits % divisor !== 0n) {
            throw new Error('Số tiền có quá hai chữ số thập phân.');
        }
        digits /= divisor;
    }
    return match[1] ? -digits : digits;
}

export function money(value) {
    const cents = decimalCents(value);
    const magnitude = cents < 0n ? -cents : cents;
    const fraction = (magnitude % 100n).toString().padStart(CENTS_SCALE, '0');
    const parts = CURRENCY.formatToParts(magnitude / 100n);
    const formatted = parts.map((part) => {
        return part.type === 'fraction' ? fraction : part.value;
    }).join('');
    return `${cents < 0n ? '−' : ''}${formatted}`;
}

export function date(value) {
    if (!value) {
        return '—';
    }
    const [year, month, day] = value.slice(0, 10).split('-');
    return `${day}/${month}/${year}`;
}

export function today() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

export function currentMonth() {
    return today().slice(0, 7);
}

export function isoWeek(value = today()) {
    const day = new Date(`${value}T00:00:00Z`);
    const weekday = day.getUTCDay() || 7;
    day.setUTCDate(day.getUTCDate() + 4 - weekday);
    const first = new Date(Date.UTC(day.getUTCFullYear(), 0, 1));
    const days = Math.floor((day - first) / 86400000) + 1;
    const week = Math.ceil(days / 7);
    return `${day.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}
