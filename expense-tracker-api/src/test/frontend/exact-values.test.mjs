import assert from 'node:assert/strict';
import {test} from 'node:test';
import {parseResponse} from '../../main/resources/static/js/api.js';
import {date, decimalCents, isoWeek, money} from '../../main/resources/static/js/format.js';
import {moneyValue, requireDate, requireEmail, requirePassword, requirePeriod, requireRange,
    requireText, ValidationError} from '../../main/resources/static/js/validation.js';
import {PERIOD_TYPES} from '../../main/resources/static/js/constants.js';

const MAXIMUM_AMOUNT = '9999999999999.99';

test('maximum DECIMAL(15,2) remains an exact decimal string', () => {
    assert.equal(moneyValue(MAXIMUM_AMOUNT), MAXIMUM_AMOUNT);
    assert.equal(decimalCents(MAXIMUM_AMOUNT), 999999999999999n);
    assert.match(money(MAXIMUM_AMOUNT), /^9\.999\.999\.999\.999,99\s*₫$/u);
});

for (const invalid of ['10000000000000.00', '0', '0.00', '-1', '0.001', '1.001', 'NaN', '1e3', '1,000.50']) {
    test(`rejects an amount that cannot be sent as positive DECIMAL(15,2): ${invalid}`, () => {
        assert.throws(() => moneyValue(invalid, 'limitAmount'), (error) => {
            assert.ok(error instanceof ValidationError);
            assert.equal(error.fieldErrors[0].field, 'limitAmount');
            return true;
        });
    });
}

test('normalizes user decimal separators without passing through Number', () => {
    assert.equal(moneyValue(' 000125000,5 '), '125000.50');
    assert.equal(moneyValue('0.01'), '0.01');
    assert.equal(moneyValue('125000'), '125000.00');
});

test('parses money and Long identifiers losslessly while retaining numeric page metadata', () => {
    const response = parseResponse('{"content":[{"id":9223372036854775807,"categoryId":9007199254740993,'
        + '"amount":9999999999999.99}],"number":0,"size":10,"totalElements":1}');
    assert.equal(response.content[0].id, '9223372036854775807');
    assert.equal(response.content[0].categoryId, '9007199254740993');
    assert.equal(response.content[0].amount, MAXIMUM_AMOUNT);
    assert.equal(response.number, 0);
    assert.equal(response.size, 10);
    assert.equal(response.totalElements, 1);
});

test('parses nested dashboard and budget money including negative and exponent tokens', () => {
    const response = parseResponse('{"totalIncome":1e3,"totalExpense":1000.01,"balance":-0.01,'
        + '"budget":{"limitAmount":500.00},"spent":501.25,"remaining":-1.25,"exceededAmount":1.25}');
    assert.equal(response.totalIncome, '1e3');
    assert.equal(response.balance, '-0.01');
    assert.equal(response.budget.limitAmount, '500.00');
    assert.equal(response.spent, '501.25');
    assert.equal(response.remaining, '-1.25');
    assert.equal(response.exceededAmount, '1.25');
    assert.equal(decimalCents(response.totalIncome), 100000n);
});

test('JSON lexer leaves escaped user strings, quoted numbers, booleans and null untouched', () => {
    const payload = {name: '<img src=x onerror=alert(1)>', note: '"amount":9999999999999.99 \\ \n',
        amount: MAXIMUM_AMOUNT, enabled: true, optional: null, nested: [{name: 'a:b,c'}]};
    assert.deepEqual(parseResponse(JSON.stringify(payload)), payload);
});

test('JSON lexer recognizes escaped monetary keys and rejects malformed JSON', () => {
    assert.equal(parseResponse('{"amou\\u006et":125000.50}').amount, '125000.50');
    assert.throws(() => parseResponse('{"amount":01.50}'), SyntaxError);
    assert.throws(() => parseResponse('{"amount":1.50,}'), SyntaxError);
});

test('formatting preserves negative cents and large aggregated values without rounding', () => {
    assert.match(money('-0.01'), /^−0,01\s*₫$/u);
    assert.match(money('19999999999999.98'), /^19\.999\.999\.999\.999,98\s*₫$/u);
    assert.match(money('1.25e2'), /^125,00\s*₫$/u);
    assert.throws(() => decimalCents('1.001'));
    assert.throws(() => decimalCents('NaN'));
});

test('formats dates as dd/MM/yyyy without timezone conversion', () => {
    assert.equal(date('2026-11-08'), '08/11/2026');
    assert.equal(date('2026-11-08T00:00:00'), '08/11/2026');
});

test('password validation uses UTF-8 bytes and preserves significant whitespace', () => {
    assert.equal(requirePassword('é'.repeat(36), 'password'), 'é'.repeat(36));
    assert.throws(() => requirePassword('é'.repeat(37), 'password'), ValidationError);
    assert.throws(() => requirePassword('1234567', 'password'), ValidationError);
    assert.throws(() => requirePassword('        ', 'password'), ValidationError);
    assert.equal(requirePassword(' short ', 'currentPassword', 1), ' short ');
    assert.equal(requirePassword(' 12345678 ', 'newPassword'), ' 12345678 ');
});

test('normalizes email and names using backend limits without requiring a public TLD', () => {
    assert.equal(requireEmail(' USER@LOCALHOST '), 'user@localhost');
    assert.equal(requireText('  Tên Việt  ', 'displayName'), 'Tên Việt');
    assert.throws(() => requireEmail('not-an-email'), ValidationError);
    assert.throws(() => requireEmail(`${'a'.repeat(65)}@example.invalid`), ValidationError);
    assert.throws(() => requireText('a'.repeat(101), 'name'), ValidationError);
});

test('date validation rejects impossible days, unsupported years and reversed ranges', () => {
    assert.equal(requireDate('2024-02-29'), '2024-02-29');
    assert.equal(requireDate('1000-01-01'), '1000-01-01');
    assert.equal(requireDate('9999-12-31'), '9999-12-31');
    assert.throws(() => requireDate('2026-02-29'), ValidationError);
    assert.throws(() => requireDate('0999-12-31'), ValidationError);
    assert.throws(() => requireRange('2026-11-02', '2026-11-01'), ValidationError);
    assert.doesNotThrow(() => requireRange('2026-11-01', '2026-11-01'));
    assert.doesNotThrow(() => requireRange('', '2026-11-01'));
});

test('budget periods validate real ISO week 53 and reject nonexistent week 53', () => {
    assert.equal(requirePeriod(PERIOD_TYPES.MONTHLY, '2024-02'), '2024-02');
    assert.equal(requirePeriod(PERIOD_TYPES.WEEKLY, '2020-W53'), '2020-W53');
    assert.throws(() => requirePeriod(PERIOD_TYPES.MONTHLY, '2024-13'), ValidationError);
    assert.throws(() => requirePeriod(PERIOD_TYPES.WEEKLY, '2021-W53'), ValidationError);
    assert.throws(() => requirePeriod(PERIOD_TYPES.WEEKLY, '2020-W00'), ValidationError);
    assert.equal(isoWeek('2021-01-01'), '2020-W53');
    assert.equal(isoWeek('2021-01-04'), '2021-W01');
});
