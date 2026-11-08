let fieldSequence = 0;

export function el(tag, options = {}, children = []) {
    const node = document.createElement(tag);
    if (options.text !== undefined) {
        node.textContent = String(options.text);
    }
    if (options.className) {
        node.className = options.className;
    }
    applyOptions(node, options);
    for (const child of children) {
        if (child !== null && child !== undefined) {
            node.append(child instanceof Node ? child : document.createTextNode(String(child)));
        }
    }
    return node;
}

function applyOptions(node, options) {
    for (const [name, value] of Object.entries(options.attrs || {})) {
        node.setAttribute(name, String(value));
    }
    for (const [name, listener] of Object.entries(options.on || {})) {
        node.addEventListener(name, listener);
    }
    for (const [name, value] of Object.entries(options)) {
        if (!['text', 'className', 'attrs', 'on'].includes(name)) {
            if (name === 'inputmode') {
                node.setAttribute(name, value);
            } else {
                node[name] = value;
            }
        }
    }
}

export function clear(node) {
    node.replaceChildren();
}

export function button(label, handler, className = 'button') {
    const node = el('button', {type: 'button', text: label, className});
    if (handler) {
        node.addEventListener('click', handler);
    }
    return node;
}

export function notice(message, kind = 'info') {
    const node = el('div', {className: `notice notice-${kind}`, text: message,
        attrs: {role: kind === 'error' ? 'alert' : 'status'}});
    return node;
}

export function pageHeader(title, subtitle, actions = []) {
    const description = el('div', {}, [el('p', {className: 'eyebrow', text: 'KHÔNG GIAN TÀI CHÍNH CỦA BẠN'}),
        el('h1', {text: title, tabIndex: -1}), el('p', {className: 'muted', text: subtitle})]);
    return el('header', {className: 'page-header'}, [description, el('div', {className: 'action-row'}, actions)]);
}

export function field(name, label, options = {}) {
    fieldSequence += 1;
    const identifier = `field-${name}-${fieldSequence}`;
    const input = createInput(name, options);
    input.id = identifier;
    const error = el('span', {className: 'field-error', attrs: {id: `${identifier}-error`, 'aria-live': 'polite'}});
    input.setAttribute('aria-describedby', `${identifier}-error`);
    const wrapper = el('div', {className: 'field'}, [el('label', {text: label, htmlFor: identifier}), input]);
    if (options.hint) {
        const hint = el('small', {text: options.hint, className: 'muted', id: `${identifier}-hint`});
        input.setAttribute('aria-describedby', `${identifier}-hint ${identifier}-error`);
        wrapper.append(hint);
    }
    wrapper.append(error);
    return {wrapper, input, error};
}

function createInput(name, options) {
    const {options: choices, hint, value, ...attributes} = options;
    const input = el(choices ? 'select' : 'input', {name, ...attributes});
    if (choices) {
        for (const [optionValue, optionLabel] of choices) {
            input.append(el('option', {value: String(optionValue), text: optionLabel}));
        }
    }
    if (value !== undefined && value !== null) {
        input.value = String(value);
    }
    return input;
}
