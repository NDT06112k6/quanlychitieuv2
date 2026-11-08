import { API, HTTP_METHODS, LIMITS, PAGE_SIZE, PAGE_SIZE_OPTIONS, TYPES } from '../constants.js';
import { request } from '../api.js';
import { categoryField } from '../categoryPicker.js';
import { button, el, field, notice, pageHeader } from '../dom.js';
import { date, money, today } from '../format.js';
import { closePanelButton, createForm, isActive, pagination, showPageError, table } from '../ui.js';
import { moneyValue, requireDate, requireRange } from '../validation.js';

const SORT_OPTIONS = [['id,desc', 'Mới ghi gần đây'], ['id,asc', 'Ghi trước đây'],
    ['occurredOn,desc', 'Ngày: mới → cũ'], ['occurredOn,asc', 'Ngày: cũ → mới'],
    ['amount,desc', 'Số tiền: cao → thấp'], ['amount,asc', 'Số tiền: thấp → cao']];
const TYPE_OPTIONS = [['', 'Tất cả loại'], [TYPES.EXPENSE, 'Chi tiêu'], [TYPES.INCOME, 'Thu nhập']];
const DATE_ATTRIBUTES = { min: '1000-01-01', max: '9999-12-31' };

export async function render(context) {
    const state = createState(context);
    const addButton = button('＋ Thêm giao dịch', async () => {
        await openEditor(state);
    });
    context.container.append(pageHeader('Giao dịch', 'Ghi lại từng khoản thu, chi để hiểu dòng tiền của bạn.', [addButton]));
    context.container.append(state.feedback, state.filters, state.editor, state.list);
    await createFilters(state);
    await loadTransactions(state);
}

function createState(context) {
    const state = { context, page: 0, size: PAGE_SIZE, sort: 'id,desc', version: 0, editorVersion: 0, query: {} };
    state.feedback = el('div', { attrs: { 'aria-live': 'polite' } });
    state.filters = el('section', { className: 'panel filters' });
    state.editor = el('section', { className: 'panel editor-panel' });
    state.editor.hidden = true;
    state.list = el('section', { className: 'panel list-panel', attrs: { 'aria-label': 'Danh sách giao dịch' } });
    return state;
}

async function createFilters(state) {
    const category = await categoryField({ optional: true, signal: state.context.signal });
    if (!isActive(state.context)) {
        return;
    }
    const fields = [field('startDate', 'Từ ngày', { type: 'date', ...DATE_ATTRIBUTES }),
        field('endDate', 'Đến ngày', { type: 'date', ...DATE_ATTRIBUTES }), category,
        field('type', 'Loại giao dịch', { options: TYPE_OPTIONS }),
        field('sort', 'Sắp xếp', { options: SORT_OPTIONS }), field('size', 'Hiển thị', { options: PAGE_SIZE_OPTIONS })];
    const form = createForm({ fields, submitLabel: 'Lọc giao dịch', onSubmit: async (values) => {
        requireRange(values.startDate, values.endDate);
        state.query = { startDate: values.startDate, endDate: values.endDate, categoryId: values.categoryId, type: values.type };
        state.sort = values.sort;
        state.size = Number(values.size);
        state.page = 0;
        await loadTransactions(state);
    } });
    state.filters.replaceChildren(form.element);
}

async function loadTransactions(state) {
    const version = ++state.version;
    state.list.replaceChildren(notice('Đang tải giao dịch…'));
    try {
        const query = { ...state.query, page: state.page, size: state.size, sort: state.sort };
        const page = await request(API.TRANSACTIONS, { query, signal: state.context.signal });
        if (version !== state.version || !isActive(state.context)) {
            return;
        }
        drawTransactions(state, page);
    } catch (error) {
        if (version === state.version) {
            showPageError(state.context, error, state.list);
        }
    }
}

function drawTransactions(state, page) {
    state.list.replaceChildren(el('div', { className: 'section-heading' }, [el('h2', { text: 'Lịch sử giao dịch' }),
        el('span', { className: 'muted', text: `${page.totalElements} giao dịch` })]));
    if (page.content.length === 0) {
        state.list.append(notice('Chưa có giao dịch phù hợp. Hãy thử thay bộ lọc hoặc ghi giao dịch đầu tiên.'));
    } else {
        const rows = page.content.map((transaction) => transactionRow(state, transaction));
        state.list.append(table(['Ngày', 'Danh mục', 'Ghi chú', 'Số tiền', 'Thao tác'], rows));
    }
    state.list.append(pagination(page, async (number) => {
        state.page = number;
        await loadTransactions(state);
    }));
}

function transactionRow(state, transaction) {
    const typeClass = transaction.type === TYPES.EXPENSE ? 'amount expense' : 'amount income';
    const prefix = transaction.type === TYPES.EXPENSE ? '− ' : '+ ';
    const edit = button('Sửa', async () => {
        await openEditor(state, transaction);
    }, 'button button-secondary');
    const remove = button('Xóa', async () => {
        await deleteTransaction(state, transaction, remove);
    }, 'button button-danger');
    const actions = el('div', { className: 'table-actions' }, [edit, remove]);
    const row = [date(transaction.occurredOn), transaction.categoryName, transaction.note || '—',
        el('span', { className: typeClass, text: prefix + money(transaction.amount) }), actions];
    return row;
}

async function openEditor(state, transaction = null) {
    const editorVersion = ++state.editorVersion;
    state.editor.hidden = false;
    state.editor.replaceChildren(notice('Đang chuẩn bị biểu mẫu…'));
    try {
        const fields = await transactionFields(state, transaction);
        if (!isActive(state.context) || editorVersion !== state.editorVersion) {
            return;
        }
        const form = createForm({ fields, submitLabel: transaction ? 'Lưu thay đổi' : 'Ghi giao dịch',
            onSubmit: async (values) => {
                await saveTransaction(state, transaction, values);
            } });
        state.editor.replaceChildren(el('h2', { text: transaction ? 'Sửa giao dịch' : 'Thêm giao dịch' }),
            notice('Khoản thu hoặc chi được xác định theo loại danh mục. Số tiền dùng dấu chấm thập phân, không dùng dấu phân cách hàng nghìn.'),
            form.element, closePanelButton(state.editor));
        fields[0].input.focus();
    } catch (error) {
        if (editorVersion === state.editorVersion) {
            showPageError(state.context, error, state.editor);
        }
    }
}

async function transactionFields(state, transaction) {
    const selected = transaction ? { id: transaction.categoryId, name: transaction.categoryName } : undefined;
    const category = await categoryField({ selected, signal: state.context.signal });
    const fields = [category,
        field('amount', 'Số tiền', { value: transaction?.amount ?? '', required: true, inputMode: 'decimal', placeholder: '125000.50' }),
        field('occurredOn', 'Ngày giao dịch', { type: 'date', value: transaction?.occurredOn ?? today(), required: true, ...DATE_ATTRIBUTES }),
        field('note', 'Ghi chú', { value: transaction?.note ?? '', maxLength: LIMITS.NOTE })];
    return fields;
}

async function saveTransaction(state, transaction, values) {
    const body = { categoryId: values.categoryId, amount: moneyValue(values.amount, 'amount'),
        occurredOn: requireDate(values.occurredOn, 'occurredOn'), note: values.note || null };
    const path = transaction ? `${API.TRANSACTIONS}/${transaction.id}` : API.TRANSACTIONS;
    const method = transaction ? HTTP_METHODS.PUT : HTTP_METHODS.POST;
    await request(path, { method, body, signal: state.context.signal });
    if (!isActive(state.context)) {
        return;
    }
    state.editor.hidden = true;
    state.page = 0;
    await loadTransactions(state);
    state.feedback.replaceChildren(notice(transaction ? 'Đã cập nhật giao dịch.' : 'Đã ghi giao dịch mới.', 'success'));
}


async function deleteTransaction(state, transaction, control) {
    if (!window.confirm(`Xóa giao dịch ${money(transaction.amount)} ngày ${date(transaction.occurredOn)}?`)) {
        return;
    }
    control.disabled = true;
    try {
        await request(`${API.TRANSACTIONS}/${transaction.id}`, { method: HTTP_METHODS.DELETE, signal: state.context.signal });
        if (!isActive(state.context)) {
            return;
        }
        state.page = 0;
        await loadTransactions(state);
        state.feedback.replaceChildren(notice('Đã xóa giao dịch.', 'success'));
    } catch (error) {
        showPageError(state.context, error, state.feedback);
    } finally {
        control.disabled = false;
    }
}

