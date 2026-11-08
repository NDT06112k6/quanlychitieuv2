import {API, PAGE_SIZE} from './constants.js';
import {request} from './api.js';
import {field, el, button, clear, notice} from './dom.js';

export async function categoryField(options = {}) {
    const selected = options.selected;
    const identifier = selected?.id ?? selected ?? '';
    const selectedName = selected?.name || selected?.categoryName || `Danh mục #${identifier}`;
    const entry = field(options.name || 'categoryId', options.label || 'Danh mục', {
        options: [['', options.optional ? 'Tất cả danh mục' : 'Chọn danh mục']], required: !options.optional});
    const feedback = el('div');
    const paging = el('div', {className: 'picker-paging'});
    entry.wrapper.append(paging, feedback);
    const state = {page: 0, selected: String(identifier), selectedName};
    entry.input.addEventListener('change', () => {
        state.selected = entry.input.value;
        state.selectedName = entry.input.selectedOptions[0]?.textContent || '';
    });
    await loadCategoryPage({entry, state, options, paging, feedback});
    return entry;
}

async function loadCategoryPage(context) {
    const {entry, state, options} = context;
    const page = await request(API.CATEGORIES, {query: {page: state.page, size: PAGE_SIZE, type: options.type}, signal: options.signal});
    if (options.signal?.aborted) {
        return;
    }
    clear(entry.input);
    entry.input.append(el('option', {value: '', text: options.optional ? 'Tất cả danh mục' : 'Chọn danh mục'}));
    if (state.selected && !page.content.some((category) => String(category.id) === state.selected)) {
        entry.input.append(el('option', {value: state.selected, text: state.selectedName}));
    }
    for (const category of page.content) {
        entry.input.append(el('option', {value: String(category.id), text: category.name}));
    }
    entry.input.value = state.selected;
    renderPickerPaging(context, page);
}

function renderPickerPaging(context, page) {
    clear(context.paging);
    const previous = button('←', () => changeCategoryPage(context, -1), 'button button-small button-secondary');
    const next = button('→', () => changeCategoryPage(context, 1), 'button button-small button-secondary');
    previous.setAttribute('aria-label', 'Trang danh mục trước');
    next.setAttribute('aria-label', 'Trang danh mục sau');
    previous.disabled = page.number <= 0;
    next.disabled = page.number + 1 >= page.totalPages;
    context.paging.append(previous, el('small', {text: `Danh mục: trang ${page.number + 1} / ${Math.max(1, page.totalPages)}`}), next);
}

async function changeCategoryPage(context, offset) {
    clear(context.feedback);
    context.state.page += offset;
    for (const control of context.paging.querySelectorAll('button')) {
        control.disabled = true;
    }
    try {
        await loadCategoryPage(context);
    } catch (error) {
        context.state.page -= offset;
        if (error.name !== 'AbortError') {
            context.feedback.append(notice(error.message, 'error'), button('Thử lại', () => changeCategoryPage(context, offset)));
        }
    }
}
