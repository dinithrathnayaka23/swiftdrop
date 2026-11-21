import { filtersFromParams, hasFilters, pageFromParams, toParams } from './orderFilters';

const params = (query: string) => new URLSearchParams(query);

test('reads every filter from the address', () => {
  expect(
    filtersFromParams(params('search=%20kandy%20&status=DELIVERED&from=2026-11-01&to=2026-11-30')),
  ).toEqual({ search: 'kandy', status: 'DELIVERED', from: '2026-11-01', to: '2026-11-30' });
});

test.each([
  ['an unknown status', 'status=LOST'],
  ['a lower-case status', 'status=delivered'],
  ['a date in another format', 'from=01/11/2026'],
  ['a date without leading zeros', 'to=2026-11-3'],
  ['a blank search', 'search=%20%20'],
])('ignores %s', (_, query) => {
  expect(hasFilters(filtersFromParams(params(query)))).toBe(false);
});

test('writes only the filters that are set, and the page one-based', () => {
  expect(toParams({ search: 'kandy', status: undefined, to: '2026-11-30' }, 2)).toEqual({
    search: 'kandy',
    to: '2026-11-30',
    page: '3',
  });
  expect(toParams({})).toEqual({});
});

test('round-trips through the address', () => {
  const filters = {
    search: 'ORD-1',
    status: 'FAILED',
    from: '2026-11-01',
    to: '2026-11-02',
  } as const;

  expect(filtersFromParams(new URLSearchParams(toParams(filters)))).toEqual(filters);
});

test.each([
  ['', 0],
  ['page=1', 0],
  ['page=3', 2],
  ['page=0', 0],
  ['page=-2', 0],
  ['page=1.5', 0],
  ['page=abc', 0],
])('page "%s" is page index %i', (query, expected) => {
  expect(pageFromParams(params(query))).toBe(expected);
});
