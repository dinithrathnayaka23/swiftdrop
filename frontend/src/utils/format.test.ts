import { formatRole, initials } from './format';

describe('format', () => {
  it.each([
    ['Ayesha Khan', 'AK'],
    ['BrightMart', 'B'],
    ['  nimal  de silva ', 'NS'],
    ['', '?'],
  ])('initials(%j) is %s', (name, expected) => {
    expect(initials(name)).toBe(expected);
  });

  it('labels roles for people', () => {
    expect(formatRole('DISPATCHER_ADMIN')).toBe('Dispatcher / Admin');
  });
});
