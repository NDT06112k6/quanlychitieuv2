import { API, HTTP_METHODS, LIMITS, PAGE_SIZE, PAGE_SIZE_OPTIONS, TYPES } from '../constants.js';
import { request } from '../api.js';
import { button, el, field, notice, pageHeader } from '../dom.js';
import { closePanelButton, createForm, isActive, pagination, showPageError, table } from '../ui.js';
import { requireText } from '../validation.js';

const TYPE_OPTIONS = [['', 'Tất cả loại'], [TYPES.EXPENSE, 'Chi tiêu'], [TYPES.INCOME, 'Thu nhập']];
const SORT_OPTIONS = [['id,desc', 'Mới nhất'], ['id,asc', 'Cũ nhất'], ['name,asc', 'Tên A → Z'], ['name,desc', 'Tên Z → A']];
const CONFLICT_STATUS = 409;

export async function render(context) {
    const state = createState(context);
    const addButton = button('＋ Thêm danh mục', () => {
        openEditor(state);
    });
    context.container.append(pageHeader('Danh mục', 'Sắp xếp thu nhập và chi tiêu theo cách của bạn.', [addButton]));
    context.container.append(state.feedback, createFilters(state), state.editor, state.list);
    await loadCategories(state);
}

function createState(context) {
    const state = { context, page: 0, size: PAGE_SIZE, sort: 'id,desc', type: '', version: 0 };
    state.feedback = el('div', { attrs: { 'aria-live': 'polite' } });
    state.editor = el('section', { className: 'panel editor-panel' });
    state.editor.hidden = true;
    state.list = el('section', { className: 'panel list-panel', attrs: { 'aria-label': 'Danh sách danh mục' } });
    return state;
}

function createFilters(state) {
    const fields = [field('type', 'Loại danh mục', { options: TYPE_OPTIONS }),
        field('sort', 'Sắp xếp', { options: SORT_OPTIONS }), field('size', 'Hiển thị', { options: PAGE_SIZE_OPTIONS })];
    const filters = createForm({ fields, submitLabel: 'Áp dụng', onSubmit: async (values) => {
        state.type = values.type;
        state.sort = values.sort;
        state.size = Number(values.size);
        state.page = 0;
        await loadCategories(state);
    } });
    const wrapper = el('section', { className: 'panel filters' }, [filters.element]);
    return wrapper;
}

async function loadCategories(state) {
    const version = ++state.version;
    state.list.replaceChildren(notice('Đang tải danh mục…'));
    try {
        const page = await request(API.CATEGORIES, { query: categoryQuery(state), signal: state.context.signal });
        if (version !== state.version || !isActive(state.context)) {
            return;
        }
        drawCategories(state, page);
    } catch (error) {
        if (version === state.version) {
            showPageError(state.context, error, state.list);
        }
    }
}

function categoryQuery(state) {
    const query = { page: state.page, size: state.size, sort: state.sort, type: state.type };
    return query;
}

function drawCategories(state, page) {
    state.list.replaceChildren(el('div', { className: 'section-heading' }, [el('h2', { text: 'Danh mục của bạn' }),
        el('span', { className: 'muted', text: `${page.totalElements} danh mục` })]));
    if (page.content.length === 0) {
        state.list.append(notice('Chưa có danh mục phù hợp. Thêm một danh mục để bắt đầu ghi chép.'));
    } else {
        const rows = page.content.map((category) => categoryRow(state, category));
        state.list.append(table(['Tên danh mục', 'Loại', 'Thao tác'], rows));
    }
    state.list.append(pagination(page, async (number) => {
        state.page = number;
        await loadCategories(state);
    }));
}

function categoryRow(state, category) {
    const typeLabel = category.type === TYPES.EXPENSE ? 'Chi tiêu' : 'Thu nhập';
    const badgeClass = category.type === TYPES.EXPENSE ? 'badge badge-expense' : 'badge badge-income';
    const rename = button('Đổi tên', () => {
        openEditor(state, category);
    }, 'button button-secondary');
    const remove = button('Xóa', async () => {
        await deleteCategory(state, category, remove);
    }, 'button button-danger');
    const actions = el('div', { className: 'table-actions' }, [rename, remove]);
    const row = [category.name, el('span', { className: badgeClass, text: typeLabel }), actions];
    return row;
}

function openEditor(state, category = null) {
    const fields = [field('name', 'Tên danh mục', { value: category?.name ?? '', required: true, maxLength: LIMITS.NAME })];
    if (!category) {
        fields.push(field('type', 'Loại danh mục', { options: TYPE_OPTIONS.slice(1), required: true }));
    }
    const form = createForm({ fields, submitLabel: category ? 'Lưu tên mới' : 'Tạo danh mục', onSubmit: async (values) => {
        await saveCategory(state, category, values);
    } });
    state.editor.replaceChildren(el('h2', { text: category ? 'Đổi tên danh mục' : 'Thêm danh mục' }),
        notice('Loại danh mục được giữ nguyên sau khi tạo.'), form.element, closePanelButton(state.editor));
    state.editor.hidden = false;
    fields[0].input.focus();
}

async function saveCategory(state, category, values) {
    const body = { name: requireText(values.name, 'name', LIMITS.NAME) };
    let path = API.CATEGORIES;
    let method = HTTP_METHODS.POST;
    if (category) {
        path = `${API.CATEGORIES}/${category.id}`;
        method = HTTP_METHODS.PUT;
    } else {
        body.type = values.type;
    }
    await request(path, { method, body, signal: state.context.signal });
    if (!isActive(state.context)) {
        return;
    }
    state.editor.hidden = true;
    state.page = 0;
    await loadCategories(state);
    state.feedback.replaceChildren(notice(category ? 'Đã đổi tên danh mục.' : 'Đã tạo danh mục mới.', 'success'));
}


async function deleteCategory(state, category, control) {
    if (!window.confirm(`Xóa danh mục “${category.name}”? Danh mục đang được sử dụng sẽ không thể xóa.`)) {
        return;
    }
    control.disabled = true;
    try {
        await request(`${API.CATEGORIES}/${category.id}`, { method: HTTP_METHODS.DELETE, signal: state.context.signal });
        if (!isActive(state.context)) {
            return;
        }
        state.page = 0;
        await loadCategories(state);
        state.feedback.replaceChildren(notice('Đã xóa danh mục.', 'success'));
    } catch (error) {
        showActionError(state, error);
    } finally {
        control.disabled = false;
    }
}


function showActionError(state, error) {
    if (error.status === CONFLICT_STATUS) {
        error.message = 'Danh mục đang được dùng bởi giao dịch hoặc ngân sách. Hãy xử lý các dữ liệu liên quan trước.';
    }
    showPageError(state.context, error, state.feedback);
}
