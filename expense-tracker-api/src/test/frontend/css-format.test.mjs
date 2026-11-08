import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import {parseCss, verifyCss} from './css-normalize.mjs';

test('formatted production CSS retains ordered rules, declarations and values', async () => {
    const original = await readFile(new URL('./fixtures/style.before-format.css', import.meta.url), 'utf8');
    const current = await readFile(new URL('../../main/resources/static/css/style.css', import.meta.url), 'utf8');
    const manifest = JSON.parse(await readFile(new URL('./fixtures/css-variables.json', import.meta.url), 'utf8'));
    const result = verifyCss(original, current, manifest);
    assert.ok(result.rules > 0);
    assert.ok(result.declarations > 0);
});

test('normalizes comments, formatting whitespace and leading decimal zeroes', () => {
    const before = '.a, :is(.b,.c) { margin: .5rem; letter-spacing: -.035em; }';
    const after = '/* group */\n.a,\n:is(.b, .c) {\n  margin: 0.5rem;\n  letter-spacing: -0.035em;\n}\n';
    assert.equal(verifyCss(before, after).declarations, 2);
});

test('normalizes optional selector spacing around combinators and functional arguments', () => {
    const before = '.a>.b, :is(.c,.d) [title] { margin: .5rem; }';
    const after = '.a > .b,\n:is( .c, .d ) [title] { margin: 0.5rem; }';
    assert.equal(verifyCss(before, after).rules, 1);
});

test('permits declared exact-value variable extraction and reuse of existing root variables', () => {
    const before = ':root { --ink: #233438; } .a { color: #233438; gap: .5rem; }';
    const after = ':root { --ink: #233438; --space-small: 0.5rem; } .a { color: var(--ink); gap: var(--space-small); }';
    const result = verifyCss(before, after, {'--space-small': '.5rem'});
    assert.equal(result.variables, 1);
});

test('preserves nested media and keyframe ordering', () => {
    const before = '@media (max-width: 730px) { .a { gap: .5rem; } } @keyframes enter { from { opacity: 0; } to { opacity: 1; } }';
    const after = '@media (max-width: 730px) {\n .a { gap: 0.5rem; }\n}\n@keyframes enter {\n from { opacity: 0; }\n to { opacity: 1; }\n}';
    assert.equal(verifyCss(before, after).rules, 5);
});

const REJECTED_CHANGES = [
    ['changed value', '.a { color: red; }', '.a { color: blue; }'],
    ['deleted declaration', '.a { color: red; gap: 1rem; }', '.a { color: red; }'],
    ['added declaration', '.a { color: red; }', '.a { color: red; gap: 1rem; }'],
    ['declaration order', '.a { color: red; color: blue; }', '.a { color: blue; color: red; }'],
    ['rule order', '.a { color: red; } .b { color: blue; }', '.b { color: blue; } .a { color: red; }'],
    ['selector order', '.a, .b { color: red; }', '.b, .a { color: red; }'],
    ['media condition', '@media (max-width: 730px) { .a { color: red; } }', '@media (max-width: 731px) { .a { color: red; } }'],
    ['important flag', '.a { color: red !important; }', '.a { color: red; }'],
    ['quoted whitespace', '.a { content: "a  b"; }', '.a { content: "a b"; }'],
    ['quoted comment text', '.a { content: "/* keep */"; }', '.a { content: ""; }'],
    ['descendant selector', '.a .b { color: red; }', '.a.b { color: red; }'],
    ['descendant pseudo selector', 'a :hover { color: red; }', 'a:hover { color: red; }'],
    ['descendant attribute selector', '.a [href] { color: red; }', '.a[href] { color: red; }'],
    ['value token separation', '.a { margin: 1px 2px; }', '.a { margin: 1px2px; }']
];

for (const [name, before, after] of REJECTED_CHANGES) {
    test(`rejects a non-formatting change: ${name}`, () => {
        assert.throws(() => verifyCss(before, after));
    });
}

test('rejects missing, changed, unlisted and duplicated new root variables', () => {
    const before = ':root { --ink: red; } .a { gap: 1rem; }';
    const manifest = {'--space': '1rem'};
    assert.throws(() => verifyCss(before, before, manifest));
    assert.throws(() => verifyCss(before, ':root { --ink: red; --space: 2rem; } .a { gap: var(--space); }', manifest));
    assert.throws(() => verifyCss(before, ':root { --ink: red; --unknown: 1rem; } .a { gap: 1rem; }'));
    assert.throws(() => verifyCss(before, ':root { --ink: red; --space: 1rem; --space: 1rem; } .a { gap: var(--space); }', manifest));
});

test('rejects shadowed managed variables and malformed CSS instead of silently passing', () => {
    const before = ':root { --ink: red; } .a { color: var(--ink); }';
    const changed = ':root { --ink: blue; } .a { color: var(--ink); }';
    assert.throws(() => verifyCss(before, changed));
    const shadowed = ':root { --ink: red; } .a { --ink: blue; color: var(--ink); }';
    assert.throws(() => verifyCss(shadowed, shadowed));
    assert.throws(() => parseCss('.a { color: red;'));
    assert.throws(() => parseCss('/* unterminated'));
    assert.throws(() => parseCss('.a { content: "unterminated; }'));
});
