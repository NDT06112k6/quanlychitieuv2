import assert from 'node:assert/strict';
import {readFile, readdir} from 'node:fs/promises';
import {test} from 'node:test';
import {el, field, notice} from '../../main/resources/static/js/dom.js';

class SafeNode {
    constructor(tag, text = '') {
        this.tag = tag;
        this.textContent = text;
        this.children = [];
        this.attributes = {};
    }

    append(child) {
        this.children.push(child);
    }

    setAttribute(name, value) {
        this.attributes[name] = String(value);
    }

    addEventListener() {}

    set innerHTML(value) {
        throw new Error('HTML parsing sink must never be used');
    }
}

function installDom() {
    globalThis.Node = SafeNode;
    globalThis.document = {createElement: (tag) => new SafeNode(tag),
        createTextNode: (text) => new SafeNode('#text', text)};
}

test('API/user strings are rendered as text nodes rather than parsed markup', () => {
    installDom();
    const payload = '<img src=x onerror=alert(1)><script>attack()</script>';
    const title = el('h2', {text: payload});
    const container = el('div', {}, [payload, title]);
    assert.equal(title.textContent, payload);
    assert.equal(container.children[0].tag, '#text');
    assert.equal(container.children[0].textContent, payload);
    assert.equal(container.children[1].tag, 'h2');
});

test('field helpers connect labels and error descriptions with unique IDs', () => {
    installDom();
    const first = field('name', 'Tên', {value: '<svg onload=attack()>', required: true});
    const second = field('name', 'Tên khác');
    assert.notEqual(first.input.id, second.input.id);
    assert.equal(first.input.value, '<svg onload=attack()>');
    assert.equal(first.wrapper.children[0].htmlFor, first.input.id);
    assert.equal(first.input.attributes['aria-describedby'], first.error.attributes.id);
    assert.equal(first.input.required, true);
});

test('error notices expose text and live-region roles without HTML parsing', () => {
    installDom();
    const error = notice('<b>Không có quyền</b>', 'error');
    assert.equal(error.textContent, '<b>Không có quyền</b>');
    assert.equal(error.attributes.role, 'alert');
    assert.equal(notice('Đã lưu', 'success').attributes.role, 'status');
});

async function sourceFiles(directory) {
    const entries = await readdir(directory, {withFileTypes: true});
    const files = [];
    for (const entry of entries) {
        const url = new URL(entry.name, directory);
        if (entry.isDirectory()) {
            files.push(...await sourceFiles(new URL(`${entry.name}/`, directory)));
        } else if (/\.(js|html|css)$/.test(entry.name)) {
            files.push(url);
        }
    }
    return files;
}

test('static frontend has no HTML parsing sinks, localStorage, dynamic code or CDN dependencies', async () => {
    const files = await sourceFiles(new URL('../../main/resources/static/', import.meta.url));
    for (const file of files) {
        const source = await readFile(file, 'utf8');
        assert.doesNotMatch(source, /\b(?:innerHTML|outerHTML|insertAdjacentHTML|document\.write|localStorage)\b/, file.pathname);
        assert.doesNotMatch(source, /\beval\s*\(|new\s+Function\s*\(/, file.pathname);
        assert.doesNotMatch(source, /(?:src|href)\s*=\s*["'](?:https?:)?\/\/|@import\s/i, file.pathname);
    }
});
