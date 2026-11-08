import { API, PAGE_SIZE, PERIOD_TYPES, ROUTES } from '../constants.js';
import { request } from '../api.js';
import { clear, el, field, notice, pageHeader } from '../dom.js';
import { currentMonth, isoWeek, money, today } from '../format.js';
import { createForm, pagination, isActive } from '../ui.js';
import { requireDate, requirePeriod, requireRange } from '../validation.js';

const CHART_MAX = 100;
const CHART_MIN = 0;
const CHART_REFERENCE = 1;
const ALERT_STATUS = Object.freeze({
    WITHIN_LIMIT: 'WITHIN_LIMIT',
    LIMIT_REACHED: 'LIMIT_REACHED',
    EXCEEDED: 'EXCEEDED'
});
const STATUS_LABELS = Object.freeze({
    [ALERT_STATUS.WITHIN_LIMIT]: 'Trong hạn mức',
    [ALERT_STATUS.LIMIT_REACHED]: 'Đã chạm hạn mức',
    [ALERT_STATUS.EXCEEDED]: 'Đã vượt ngân sách'
});
const STATUS_TONES = Object.freeze({
    [ALERT_STATUS.WITHIN_LIMIT]: 'success',
    [ALERT_STATUS.LIMIT_REACHED]: 'warning',
    [ALERT_STATUS.EXCEEDED]: 'danger'
});

export async function render(context) {
    const state = initialState();
    context.container.append(pageHeader('Tổng quan', 'Theo dõi thu chi và chủ động với kế hoạch của bạn.'));
    createSummarySection(context, state);
    createAlertSection(context, state);
    await Promise.all([loadSummary(context, state), loadAlerts(context, state)]);
}

function initialState() {
    const state = {
        summaryQuery: { startDate: `${currentMonth()}-01`, endDate: today() },
        alertQuery: { type: PERIOD_TYPES.MONTHLY, period: currentMonth(), page: 0, size: PAGE_SIZE },
        summaryRevision: 0,
        alertRevision: 0
    };
    return state;
}

function createSummarySection(context, state) {
    const start = field('startDate', 'Từ ngày', dateOptions(state.summaryQuery.startDate));
    const end = field('endDate', 'Đến ngày', dateOptions(state.summaryQuery.endDate));
    const form = createForm({
        fields: [start, end],
        submitLabel: 'Xem tổng quan',
        onSubmit: async (values) => {
            state.summaryQuery = summaryQuery(values);
            await loadSummary(context, state);
        }
    });
    form.element.classList.add('filter-form');
    state.summaryContent = el('div', { className: 'dashboard-summary', attrs: { 'aria-live': 'polite' } });
    const section = el('section', { className: 'section' }, [form.element, state.summaryContent]);
    context.container.append(section);
}

function dateOptions(value) {
    const options = { type: 'date', value, required: true, min: '1000-01-01', max: '9999-12-31' };
    return options;
}

function summaryQuery(values) {
    const startDate = requireDate(values.startDate, 'startDate');
    const endDate = requireDate(values.endDate, 'endDate');
    requireRange(startDate, endDate);
    const query = { startDate, endDate };
    return query;
}

async function loadSummary(context, state) {
    const revision = ++state.summaryRevision;
    state.summaryContent.replaceChildren(notice('Đang tổng hợp thu chi…'));
    try {
        const summary = await request(API.DASHBOARD, { query: state.summaryQuery, signal: context.signal });
        if (!isActive(context) || revision !== state.summaryRevision) {
            return;
        }
        const cards = summaryCards(summary);
        const chart = comparisonChart(summary);
        state.summaryContent.replaceChildren(cards, chart);
    } catch (error) {
        if (revision === state.summaryRevision) {
            showLoadError(context, state.summaryContent, error);
        }
    }
}

function summaryCards(summary) {
    const cards = el('div', { className: 'stats-grid' }, [
        statistic('Tổng thu', summary.totalIncome, 'income'),
        statistic('Tổng chi', summary.totalExpense, 'expense'),
        statistic('Số dư trong kỳ', summary.balance, 'balance')
    ]);
    return cards;
}

function statistic(label, amount, kind) {
    const card = el('article', { className: `stat-card ${kind}` }, [
        el('p', { className: 'stat-label', text: label }),
        el('strong', { className: 'stat-value', text: money(amount) })
    ]);
    return card;
}

function comparisonChart(summary) {
    // Conversion is limited to drawing scale; displayed and submitted money stays exact.
    const reference = Math.max(Number(summary.totalIncome), Number(summary.totalExpense), CHART_REFERENCE);
    const chart = el('section', { className: 'panel chart' }, [
        el('h2', { text: 'Thu và chi trong kỳ' }),
        chartRow('Thu nhập', summary.totalIncome, { reference, kind: 'income-meter' }),
        chartRow('Chi tiêu', summary.totalExpense, { reference, kind: 'expense-meter' }),
        el('p', { className: 'muted', text: 'Thanh so sánh được tính từ tổng thu chi của toàn bộ khoảng ngày đã chọn.' })
    ]);
    return chart;
}

function chartRow(label, amount, options) {
    const value = Math.max(CHART_MIN, Number(amount) / options.reference * CHART_MAX);
    const row = el('div', { className: 'chart-row' }, [
        el('span', { text: label }),
        el('progress', {
            className: `meter ${options.kind}`,
            attrs: { max: String(CHART_MAX), value: String(value), 'aria-label': `${label}: ${money(amount)}` }
        }),
        el('strong', { text: money(amount) })
    ]);
    return row;
}

function createAlertSection(context, state) {
    const type = field('type', 'Loại kỳ', {
        value: PERIOD_TYPES.MONTHLY,
        options: [[PERIOD_TYPES.MONTHLY, 'Theo tháng'], [PERIOD_TYPES.WEEKLY, 'Theo tuần']]
    });
    const period = field('period', 'Kỳ ngân sách', { value: currentMonth(), required: true, hint: 'Tháng: 2026-11 · Tuần ISO: 2026-W45' });
    type.input.addEventListener('change', () => {
        period.input.value = type.input.value === PERIOD_TYPES.MONTHLY ? currentMonth() : isoWeek();
    });
    const form = createAlertForm(context, state, { type, period });
    form.element.classList.add('filter-form');
    state.alertContent = el('div', { attrs: { 'aria-live': 'polite' } });
    const section = el('section', { className: 'section' }, [
        el('div', { className: 'section-heading' }, [
            el('h2', { text: 'Theo dõi ngân sách' }),
            el('p', { className: 'muted', text: 'Nhận biết hạn mức đã chạm hoặc vượt để điều chỉnh sớm.' })
        ]),
        form.element,
        state.alertContent
    ]);
    context.container.append(section);
}

function createAlertForm(context, state, fields) {
    const form = createForm({
        fields: [fields.type, fields.period],
        submitLabel: 'Xem cảnh báo',
        onSubmit: async (values) => {
            const period = requirePeriod(values.type, values.period);
            state.alertQuery = { type: values.type, period, page: 0, size: PAGE_SIZE };
            await loadAlerts(context, state);
        }
    });
    return form;
}

async function loadAlerts(context, state) {
    const revision = ++state.alertRevision;
    state.alertContent.replaceChildren(notice('Đang kiểm tra ngân sách…'));
    try {
        const page = await request(API.ALERTS, { query: state.alertQuery, signal: context.signal });
        if (!isActive(context) || revision !== state.alertRevision) {
            return;
        }
        renderAlerts(context, state, page);
    } catch (error) {
        if (revision === state.alertRevision) {
            showLoadError(context, state.alertContent, error);
        }
    }
}

function renderAlerts(context, state, page) {
    clear(state.alertContent);
    if (page.content.length === 0) {
        state.alertContent.append(notice('Chưa có ngân sách cho kỳ này. Tạo ngân sách để bắt đầu theo dõi.'));
        state.alertContent.append(el('a', {
            className: 'button button-secondary', text: 'Tạo ngân sách', attrs: { href: `#${ROUTES.BUDGETS}` }
        }));
        return;
    }
    const cards = el('div', { className: 'budget-grid' });
    for (const alert of page.content) {
        cards.append(alertCard(alert));
    }
    state.alertContent.append(cards, pagination(page, async (nextPage) => {
        state.alertQuery.page = nextPage;
        await loadAlerts(context, state);
    }));
}

function alertCard(alert) {
    const tone = STATUS_TONES[alert.status] || 'info';
    const card = el('article', { className: `panel budget-card ${tone}` }, [
        el('div', { className: 'card-heading' }, [
            el('h3', { text: alert.budget.categoryName }),
            el('span', { className: `badge ${tone}`, text: STATUS_LABELS[alert.status] || 'Trạng thái ngân sách' })
        ]),
        el('p', { className: 'muted', text: alert.budget.period.period }),
        alertMeter(alert),
        amountLine('Đã chi', alert.spent),
        amountLine('Hạn mức', alert.budget.limitAmount),
        amountLine('Còn lại', alert.remaining)
    ]);
    if (alert.status === ALERT_STATUS.EXCEEDED) {
        card.append(amountLine('Vượt hạn mức', alert.exceededAmount));
    }
    return card;
}

function alertMeter(alert) {
    const percentage = Math.min(CHART_MAX, Number(alert.spent) / Number(alert.budget.limitAmount) * CHART_MAX);
    const meter = el('progress', {
        className: 'meter',
        attrs: { max: String(CHART_MAX), value: String(percentage), 'aria-label': 'Tỷ lệ sử dụng ngân sách' }
    });
    return meter;
}

function amountLine(label, amount) {
    const line = el('p', { className: 'amount-line' }, [
        el('span', { text: label }),
        el('strong', { text: money(amount) })
    ]);
    return line;
}


function showLoadError(context, container, error) {
    if (!isActive(context) || error.name === 'AbortError') {
        return;
    }
    container.replaceChildren(notice(error.message, 'error'));
}
