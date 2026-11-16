import { daysIn, lastDays, rangeError, thisMonth, toIsoDate } from './dateRange';

const TODAY = new Date(2026, 10, 14); // 14 Nov 2026

test('formats local dates as yyyy-MM-dd', () => {
  expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
});

test('last 7 days ends today and counts both ends', () => {
  const range = lastDays(7, TODAY);
  expect(range).toEqual({ from: '2026-11-08', to: '2026-11-14' });
  expect(daysIn(range)).toBe(7);
});

test('last 30 days crosses into the previous month', () => {
  expect(lastDays(30, TODAY)).toEqual({ from: '2026-10-16', to: '2026-11-14' });
});

test('this month starts on the 1st', () => {
  expect(thisMonth(TODAY)).toEqual({ from: '2026-11-01', to: '2026-11-14' });
});

test('ranges are checked like the backend checks them', () => {
  expect(rangeError({ from: '2026-11-01', to: '2026-11-14' }, TODAY)).toBeNull();
  expect(rangeError({ from: '2026-11-14', to: '2026-11-14' }, TODAY)).toBeNull();
  expect(rangeError({ from: '', to: '2026-11-14' }, TODAY)).toMatch(/both/);
  expect(rangeError({ from: '2026-11-10', to: '2026-11-01' }, TODAY)).toMatch(/on or before/);
  expect(rangeError({ from: '2026-11-15', to: '2026-11-20' }, TODAY)).toMatch(/future/);
  expect(rangeError({ from: '2025-11-13', to: '2026-11-14' }, TODAY)).toMatch(/366 days/);
  expect(rangeError({ from: '2025-11-14', to: '2026-11-14' }, TODAY)).toBeNull(); // exactly 366
});
