import assert from 'node:assert/strict';

const PUNCTUATION = new Set('{}:;,()[]>+~=^$*|'.split(''));
const GROUP_RULES = new Set(['@media', '@supports', '@container', '@layer', '@keyframes',
    '@-webkit-keyframes', '@document', '@scope', '@starting-style']);
const OPEN_BRACKETS = new Map([['(', ')'], ['[', ']']]);

function readQuoted(source, start) {
    const quote = source[start];
    let index = start + 1;
    while (index < source.length) {
        if (source[index] === '\\') {
            index += 2;
        } else if (source[index] === quote) {
            return index + 1;
        } else {
            index += 1;
        }
    }
    throw new Error('Unterminated CSS string');
}

function readWord(source, start) {
    let index = start;
    while (index < source.length) {
        const character = source[index];
        if (/\s/.test(character) || PUNCTUATION.has(character)
                || character === '"' || character === "'" || source.startsWith('/*', index)) {
            break;
        }
        index += character === '\\' ? 2 : 1;
    }
    return index;
}

function lexemeAt(source, index) {
    const character = source[index];
    if (character === '"' || character === "'") {
        const end = readQuoted(source, index);
        return {end, token: {type: 'string', value: source.slice(index, end)}};
    }
    if (PUNCTUATION.has(character)) {
        return {end: index + 1, token: {type: 'symbol', value: character}};
    }
    const end = readWord(source, index);
    assert.ok(end > index, 'CSS scanner must advance');
    return {end, token: {type: 'word', value: source.slice(index, end)}};
}

export function tokenizeCss(source) {
    const tokens = [];
    let index = 0;
    let separated = false;
    while (index < source.length) {
        if (/\s/.test(source[index])) {
            separated = true;
            index += 1;
        } else if (source.startsWith('/*', index)) {
            const end = source.indexOf('*/', index + 2);
            assert.notEqual(end, -1, 'Unterminated CSS comment');
            index = end + 2;
        } else {
            const lexeme = lexemeAt(source, index);
            lexeme.token.spaceBefore = separated;
            tokens.push(lexeme.token);
            separated = false;
            index = lexeme.end;
        }
    }
    return tokens;
}

function takeUntil(state, delimiters) {
    const result = [];
    const brackets = [];
    while (state.index < state.tokens.length) {
        const token = state.tokens[state.index];
        if (brackets.length === 0 && delimiters.has(token.value) && token.type === 'symbol') {
            break;
        }
        if (OPEN_BRACKETS.has(token.value) && token.type === 'symbol') {
            brackets.push(OPEN_BRACKETS.get(token.value));
        } else if (token.type === 'symbol' && [')', ']'].includes(token.value)) {
            assert.equal(token.value, brackets.pop(), 'Unbalanced CSS parentheses/brackets');
        }
        result.push(token);
        state.index += 1;
    }
    assert.equal(brackets.length, 0, 'Unterminated CSS parentheses/brackets');
    return result;
}

function readDeclarations(state) {
    const declarations = [];
    while (state.index < state.tokens.length && state.tokens[state.index].value !== '}') {
        const tokens = takeUntil(state, new Set([';', '}']));
        const colon = tokens.findIndex((token) => token.type === 'symbol' && token.value === ':');
        assert.equal(colon, 1, 'Expected one CSS property before the colon');
        assert.ok(tokens.length > colon + 1, 'CSS declaration must have a value');
        declarations.push({property: tokens[0].value, value: tokens.slice(colon + 1)});
        if (state.tokens[state.index]?.value === ';') {
            state.index += 1;
        }
    }
    return declarations;
}

function readRule(state) {
    const header = takeUntil(state, new Set(['{', ';', '}']));
    assert.ok(header.length > 0, 'Expected a CSS rule header');
    const delimiter = state.tokens[state.index++]?.value;
    if (delimiter === ';') {
        assert.ok(header[0].value.startsWith('@'), 'Only at-rules may end without a block');
        return {type: 'statement', header};
    }
    assert.equal(delimiter, '{', 'Expected a CSS block');
    const group = GROUP_RULES.has(header[0].value);
    const node = group ? {type: 'group', header, rules: readRules(state, true)}
        : {type: 'rule', header, declarations: readDeclarations(state)};
    assert.equal(state.tokens[state.index++]?.value, '}', 'Expected a closing CSS brace');
    return node;
}

function readRules(state, nested = false) {
    const rules = [];
    while (state.index < state.tokens.length) {
        if (state.tokens[state.index].value === '}') {
            assert.ok(nested, 'Unexpected closing CSS brace');
            break;
        }
        rules.push(readRule(state));
    }
    return rules;
}

export function parseCss(source) {
    const state = {tokens: tokenizeCss(source), index: 0};
    const rules = readRules(state);
    assert.equal(state.index, state.tokens.length, 'All CSS tokens must be parsed');
    return rules;
}

function canonicalTokens(tokens) {
    const result = tokens.map((token) => {
        const value = token.type === 'word' ? token.value.replace(/^(-?)\.(?=\d)/, '$10.') : token.value;
        return [token.type, value];
    });
    return result;
}

function selectorGroups(tokens) {
    const state = {tokens, index: 0};
    const selectors = [];
    while (state.index < tokens.length) {
        const selector = takeUntil(state, new Set([',']));
        assert.ok(selector.length > 0, 'Empty CSS selector');
        selectors.push(canonicalSelector(selector));
        if (state.tokens[state.index]?.value === ',') {
            state.index += 1;
        }
    }
    return selectors;
}

function isDescendantBoundary(previous, current) {
    const canEnd = previous?.type === 'word' || [')', ']'].includes(previous?.value);
    const canStart = current.type === 'word' || [':', '[', '*'].includes(current.value);
    const meaningful = current.spaceBefore && canEnd && canStart;
    return meaningful;
}

function canonicalSelector(tokens) {
    const result = [];
    for (let index = 0; index < tokens.length; index += 1) {
        if (index > 0 && isDescendantBoundary(tokens[index - 1], tokens[index])) {
            result.push(['descendant', ' ']);
        }
        result.push(...canonicalTokens([tokens[index]]));
    }
    return result;
}

function isRootRule(node) {
    return node.type === 'rule' && node.header.length === 2
        && node.header[0].value === ':' && node.header[1].value === 'root';
}

function rootProperties(rules) {
    const properties = new Map();
    for (const node of rules) {
        if (!isRootRule(node)) {
            continue;
        }
        for (const declaration of node.declarations) {
            if (declaration.property.startsWith('--')) {
                assert.ok(!properties.has(declaration.property), 'Duplicate root custom property');
                properties.set(declaration.property, declaration.value);
            }
        }
    }
    return properties;
}

function validateManifest(original, candidate, manifest) {
    const baseline = rootProperties(original);
    const current = rootProperties(candidate);
    for (const [name, value] of Object.entries(manifest)) {
        assert.match(name, /^--[a-zA-Z][\w-]*$/, 'Manifest requires custom-property names');
        assert.equal(typeof value, 'string', 'Manifest values must be literal CSS strings');
        assert.ok(!baseline.has(name), `Manifest property already existed: ${name}`);
        assert.ok(current.has(name), `Manifest property missing from root: ${name}`);
        assert.deepEqual(canonicalTokens(current.get(name)), canonicalTokens(tokenizeCss(value)),
            `Manifest property value changed: ${name}`);
    }
    return current;
}

function variableAt(tokens, index) {
    const token = tokens[index];
    if (token.type !== 'word' || token.value !== 'var' || tokens[index + 1]?.value !== '(') {
        return null;
    }
    const name = tokens[index + 2]?.value;
    if (name?.startsWith('--') && tokens[index + 3]?.value === ')') {
        return name;
    }
    return null;
}

function resolveVariables(tokens, definitions, ancestors = []) {
    const result = [];
    for (let index = 0; index < tokens.length; index += 1) {
        const name = variableAt(tokens, index);
        if (name && definitions.has(name)) {
            assert.ok(!ancestors.includes(name), `Cyclic custom property: ${name}`);
            result.push(...resolveVariables(definitions.get(name), definitions, [...ancestors, name]));
            index += 3;
        } else {
            result.push(tokens[index]);
        }
    }
    return result;
}

function normalizedDeclaration(declaration, definitions) {
    const tokens = resolveVariables(declaration.value, definitions);
    return {property: declaration.property, value: canonicalTokens(tokens)};
}

function validateVariableScope(rules, definitions, nested = false) {
    for (const node of rules) {
        if (node.rules) {
            validateVariableScope(node.rules, definitions, true);
        }
        for (const declaration of node.declarations || []) {
            if (definitions.has(declaration.property)) {
                assert.ok(!nested && isRootRule(node),
                    'Managed custom properties must not be shadowed outside the top-level root');
            }
        }
    }
}

function normalizeNode(node, context) {
    const header = selectorGroups(node.header);
    if (node.type === 'group') {
        return {type: node.type, header, rules: normalizeRules(node.rules, {...context, nested: true})};
    }
    if (node.type === 'statement') {
        return {type: node.type, header};
    }
    const declarations = [];
    for (const declaration of node.declarations) {
        const introduced = Object.hasOwn(context.manifest, declaration.property);
        assert.ok(!introduced || (!context.nested && isRootRule(node)),
            'New custom properties must appear only in the top-level root rule');
        if (!introduced) {
            declarations.push(normalizedDeclaration(declaration, context.definitions));
        }
    }
    return {type: node.type, header, declarations};
}

function normalizeRules(rules, context) {
    const normalized = rules.map((node) => normalizeNode(node, context));
    return normalized;
}

function countNodes(rules) {
    const counts = {rules: 0, declarations: 0};
    for (const node of rules) {
        counts.rules += 1;
        counts.declarations += node.declarations?.length || 0;
        if (node.rules) {
            const nested = countNodes(node.rules);
            counts.rules += nested.rules;
            counts.declarations += nested.declarations;
        }
    }
    return counts;
}

export function verifyCss(originalSource, candidateSource, manifest = {}) {
    const original = parseCss(originalSource);
    const candidate = parseCss(candidateSource);
    const definitions = validateManifest(original, candidate, manifest);
    validateVariableScope(original, rootProperties(original));
    validateVariableScope(candidate, definitions);
    const before = normalizeRules(original, {definitions: rootProperties(original), manifest: {}, nested: false});
    const after = normalizeRules(candidate, {definitions, manifest, nested: false});
    assert.deepEqual(after, before, 'CSS rule/declaration order or effective values changed');
    const counts = countNodes(original);
    return {...counts, variables: Object.keys(manifest).length};
}
