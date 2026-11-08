import {readFile} from 'node:fs/promises';
import {verifyCss} from './css-normalize.mjs';

const fixture = new URL('./fixtures/style.before-format.css', import.meta.url);
const stylesheet = new URL('../../main/resources/static/css/style.css', import.meta.url);
const mapping = new URL('./fixtures/css-variables.json', import.meta.url);
const original = await readFile(fixture, 'utf8');
const current = await readFile(stylesheet, 'utf8');
const manifest = JSON.parse(await readFile(mapping, 'utf8'));
const result = verifyCss(original, current, manifest);
process.stdout.write(`CSS equivalence passed: ${result.rules} ordered rules, `
    + `${result.declarations} original declarations, ${result.variables} declared new variables.\n`);
