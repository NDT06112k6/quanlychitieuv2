import { API, HTTP_METHODS, PAGE_SIZE, PAGE_SIZE_OPTIONS, PERIOD_TYPES, TYPES } from '../constants.js';
import { request } from '../api.js';
import { categoryField } from '../categoryPicker.js';
import { button, el, field, notice, pageHeader } from '../dom.js';
import { money, today } from '../format.js';
import { closePanelButton, createForm, isActive, pagination, showPageError, table } from '../ui.js';
import { moneyValue, requirePeriod } from '../validation.js';

const PERIOD_OPTIONS = [[PERIOD_TYPES.MONTHLY, 'Theo tháng'], [PERIOD_TYPES.WEEKLY, 'Theo tuần']];
const FILTER_PERIOD_OPTIONS = [['', 'Tất cả kỳ'], ...PERIOD_OPTIONS];
const SORT_OPTIONS = [['id,desc', 'Mới nhất'], ['id,asc', 'Cũ nhất'],
    ['limitAmount,desc', 'Hạn mức: cao → thấp'], ['limitAmount,asc', 'Hạn mức: thấp → cao']];
const DUPLICATE_MESSAGE = 'Danh mục này đã có ngân sách trong kỳ đã chọn. Hãy sửa hạn mức của ngân sách hiện có.';
const CONFLICT_STATUS = 409;

export async function render(context) {
    const state = createState(context);
    const addButton = button('＋ Thêm ngân sách', async () => {
        await openEditor(state);
    });
    context.container.append(pageHeader('Ngân sách', 'Đặt giới hạn chi tiêu và chủ động với kế hoạch của bạn.', [addButton]));
    context.container.append(state.feedback, state.filters, state.editor, state.list);
    await createFilters(state);
    await loadBudgets(state);
}

function createState(context) {
    const state = { context, page: 0, size: PAGE_SIZE, sort: 'id,desc', version: 0, editorVersion: 0, query: {} };
    state.feedback = el('div', { attrs: { 'aria-live': 'polite' } });
    state.filters = el('section', { className: 'panel filters' });
    state.editor = el('section', { className: 'panel editor-panel' });
    state.editor.hidden = true;
    state.list = el('section', { className: 'panel list-panel', attrs: { 'aria-label': 'Danh sách ngân sách' } });
    return state;
}

async function createFilters(state) {
    const category = await categoryField({ optional: true, type: TYPES.EXPENSE, signal: state.context.signal });
    if (!isActive(state.context)) {
        return;
    }
    const fields = [category, field('type', 'Loại kỳ', { options: FILTER_PERIOD_OPTIONS }),
        field('period', 'Kỳ ngân sách', { placeholder: '2026-11 hoặc 2026-W45', hint: 'Để trống để xem mọi kỳ. Chọn loại kỳ khi nhập kỳ cụ thể.' }),
        field('sort', 'Sắp xếp', { options: SORT_OPTIONS }), field('size', 'Hiển thị', { options: PAGE_SIZE_OPTIONS })];
    const form = createForm({ fields, submitLabel: 'Lọc ngân sách', onSubmit: async (values) => {
        const period = values.period.trim();
        if (period) {
            requirePeriod(values.type, period);
        }
        state.query = { categoryId: values.categoryId, type: values.type, period };
        state.sort = values.sort;
        state.size = Number(values.size);
        state.page = 0;
        await loadBudgets(state);
    } });
    state.filters.replaceChildren(form.element);
}

async function loadBudgets(state) {
    const version = ++state.version;
    state.list.replaceChildren(notice('Đang tải ngân sách…'));
    try {
        const query = { ...state.query, page: state.page, size: state.size, sort: state.sort };
        const page = await request(API.BUDGETS, { query, signal: state.context.signal });
        if (version !== state.version || !isActive(state.context)) {
            return;
        }
        drawBudgets(state, page);
    } catch (error) {
        if (version === state.version) {
            showPageError(state.context, error, state.list);
        }
    }
}

function drawBudgets(state, page) {
    state.list.replaceChildren(el('div', { className: 'section-heading' }, [el('h2', { text: 'Kế hoạch chi tiêu' }),
        el('span', { className: 'muted', text: `${page.totalElements} ngân sách` })]));
    if (page.content.length === 0) {
        state.list.append(notice('Chưa có ngân sách phù hợp. Tạo ngân sách cho một danh mục chi tiêu để bắt đầu.'));
    } else {
        const rows = page.content.map((budget) => budgetRow(state, budget));
        state.list.append(table(['Danh mục', 'Kỳ áp dụng', 'Hạn mức', 'Thao tác'], rows));
    }
    state.list.append(pagination(page, async (number) => {
        state.page = number;
        await loadBudgets(state);
    }));
}

function budgetRow(state, budget) {
    const edit = button('Sửa hạn mức', async () => {
        await openEditor(state, budget);
    }, 'button button-secondary');
    const remove = button('Xóa', async () => {
        await deleteBudget(state, budget, remove);
    }, 'button button-danger');
    const actions = el('div', { className: 'table-actions' }, [edit, remove]);
    const row = [budget.categoryName, periodLabel(budget.period),
        el('span', { className: 'amount', text: money(budget.limitAmount) }), actions];
    return row;
}

function periodLabel(period) {
    let label;
    if (period.type === PERIOD_TYPES.MONTHLY) {
        const [year, month] = period.period.split('-');
        label = `Tháng ${month}/${year}`;
    } else {
        const [year, week] = period.period.split('-W');
        label = `Tuần ${week} / ${year}`;
    }
    return label;
}

async function openEditor(state, budget = null) {
    const editorVersion = ++state.editorVersion;
    state.editor.hidden = false;
    state.editor.replaceChildren(notice('Đang chuẩn bị biểu mẫu…'));
    try {
        const fields = await budgetFields(state, budget);
        if (!isActive(state.context) || editorVersion !== state.editorVersion) {
            return;
        }
        const form = createForm({ fields, submitLabel: budget ? 'Lưu hạn mức' : 'Tạo ngân sách', onSubmit: async (values) => {
            await saveBudget(state, budget, values);
        } });
        state.editor.replaceChildren(el('h2', { text: budget ? 'Sửa hạn mức ngân sách' : 'Thêm ngân sách' }),
            editorHint(budget), form.element, closePanelButton(state.editor));
        state.editor.hidden = false;
        fields[0].input.focus();
    } catch (error) {
        if (editorVersion === state.editorVersion) {
            showPageError(state.context, error, state.editor);
        }
    }
}

async function budgetFields(state, budget) {
    const limit = field('limitAmount', 'Hạn mức chi tiêu', { value: budget?.limitAmount ?? '', required: true,
        inputMode: 'decimal', placeholder: '2000000.00', hint: 'Số dương, tối đa 13 chữ số nguyên và 2 chữ số thập phân.' });
    if (budget) {
        return [limit];
    }
    const category = await categoryField({ type: TYPES.EXPENSE, signal: state.context.signal });
    const type = field('type', 'Loại kỳ', { options: PERIOD_OPTIONS, required: true });
    const period = field('period', 'Kỳ ngân sách', { type: 'month', value: today().slice(0, 7), required: true,
        min: '1000-01', max: '9999-12', hint: 'Tháng: YYYY-MM. Tuần ISO: YYYY-Www, từ thứ Hai đến Chủ nhật.' });
    type.input.addEventListener('change', () => updatePeriodInput(type, period));
    const fields = [category, type, period, limit];
    return fields;
}

function updatePeriodInput(type, period) {
    const monthly = type.input.value === PERIOD_TYPES.MONTHLY;
    period.input.type = monthly ? 'month' : 'week';
    period.input.value = monthly ? today().slice(0, 7) : '';
    period.input.removeAttribute('min');
    period.input.removeAttribute('max');
    if (monthly) {
        period.input.setAttribute('min', '1000-01');
        period.input.setAttribute('max', '9999-12');
    }
}

function editorHint(budget) {
    let text = 'Chỉ danh mục chi tiêu có thể đặt ngân sách. Số tiền dùng dấu chấm thập phân, không dùng dấu phân cách hàng nghìn.';
    if (budget) {
        text = `${budget.categoryName} · ${periodLabel(budget.period)}. Chỉ hạn mức được thay đổi; danh mục và kỳ được giữ nguyên.`;
    }
    const hint = notice(text);
    return hint;
}

async function saveBudget(state, budget, values) {
    const body = { limitAmount: moneyValue(values.limitAmount, 'limitAmount') };
    let path = API.BUDGETS;
    let method = HTTP_METHODS.POST;
    if (budget) {
        path = `${API.BUDGETS}/${budget.id}`;
        method = HTTP_METHODS.PUT;
    } else {
        body.categoryId = values.categoryId;
        body.period = { type: values.type, period: requirePeriod(values.type, values.period) };
    }
    try {
        await request(path, { method, body, signal: state.context.signal });
    } catch (error) {
        if (error.status === CONFLICT_STATUS) {
            error.message = DUPLICATE_MESSAGE;
        }
        throw error;
    }
    await finishSave(state, budget);
}

async function finishSave(state, budget) {
    if (!isActive(state.context)) {
        return;
    }
    state.editor.hidden = true;
    state.page = 0;
    await loadBudgets(state);
    state.feedback.replaceChildren(notice(budget ? 'Đã cập nhật hạn mức ngân sách.' : 'Đã tạo ngân sách mới.', 'success'));
}


async function deleteBudget(state, budget, control) {
    if (!window.confirm(`Xóa ngân sách “${budget.categoryName}” — ${periodLabel(budget.period)}?`)) {
        return;
    }
    control.disabled = true;
    try {
        await request(`${API.BUDGETS}/${budget.id}`, { method: HTTP_METHODS.DELETE, signal: state.context.signal });
        if (!isActive(state.context)) {
            return;
        }
        state.page = 0;
        await loadBudgets(state);
        state.feedback.replaceChildren(notice('Đã xóa ngân sách.', 'success'));
    } catch (error) {
        showPageError(state.context, error, state.feedback);
    } finally {
        control.disabled = false;
    }
}

