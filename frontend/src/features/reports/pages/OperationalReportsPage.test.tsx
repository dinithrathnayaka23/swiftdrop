import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiRequestError } from '../../../api/errors';
import { reportsApi } from '../api';
import { lastDays } from '../dateRange';
import type { DailyDeliveryStat, ReportSummary } from '../types';
import OperationalReportsPage from './OperationalReportsPage';

jest.mock('../api', () => ({
  reportsApi: { summary: jest.fn(), deliveriesPerDay: jest.fn() },
}));

const api = jest.mocked(reportsApi);

const SUMMARY: ReportSummary = {
  from: '2026-11-08',
  to: '2026-11-14',
  total: 120,
  delivered: 95,
  failed: 5,
  successRate: 95,
  failureRate: 5,
};

const DAYS: DailyDeliveryStat[] = [
  { date: '2026-11-12', total: 40, delivered: 30, failed: 2 },
  { date: '2026-11-13', total: 50, delivered: 35, failed: 1 },
  { date: '2026-11-14', total: 30, delivered: 0, failed: 0 },
];

beforeEach(() => {
  jest.clearAllMocks();
  api.summary.mockResolvedValue(SUMMARY);
  api.deliveriesPerDay.mockResolvedValue(DAYS);
});

test('loads the last 7 days by default and shows the totals and rates', async () => {
  render(<OperationalReportsPage />);

  expect(await screen.findByText('95.00%')).toBeInTheDocument();
  expect(screen.getByText('120')).toBeInTheDocument();
  expect(screen.getByText('5.00% failure rate')).toBeInTheDocument();
  expect(api.summary).toHaveBeenCalledWith(lastDays(7));
  expect(api.deliveriesPerDay).toHaveBeenCalledWith(lastDays(7));
  expect(screen.getByRole('button', { name: 'Last 7 days' })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
});

test('shows the chart with a legend and a table row per day', async () => {
  render(<OperationalReportsPage />);

  const chart = await screen.findByRole('group', { name: /Deliveries per day, 3 days/ });
  expect(chart).toBeInTheDocument();
  const legend = screen.getByRole('list', { name: 'Legend' });
  expect(within(legend).getByText('Created')).toBeInTheDocument();
  expect(within(legend).getByText('Delivered')).toBeInTheDocument();
  expect(within(legend).getByText('Failed')).toBeInTheDocument();

  const rows = within(screen.getByRole('table')).getAllByRole('row');
  expect(rows).toHaveLength(4); // header + 3 days, newest first
  expect(within(rows[1]).getByText('–')).toBeInTheDocument(); // nothing finished that day
  expect(within(rows[2]).getByText('97.22%')).toBeInTheDocument(); // 35 / 36
});

test('the chart can be read with the keyboard', async () => {
  render(<OperationalReportsPage />);
  const chart = await screen.findByRole('group', { name: /Deliveries per day/ });

  fireEvent.keyDown(chart, { key: 'Home' });

  const tooltip = screen.getByRole('tooltip');
  expect(within(tooltip).getByText('40')).toBeInTheDocument();
  fireEvent.keyDown(chart, { key: 'ArrowRight' });
  expect(within(screen.getByRole('tooltip')).getByText('50')).toBeInTheDocument();
  fireEvent.keyDown(chart, { key: 'Escape' });
  expect(screen.queryByRole('tooltip')).not.toBeInTheDocument();
});

test('a preset reloads the report for that range', async () => {
  render(<OperationalReportsPage />);
  await screen.findByText('95.00%');

  await userEvent.click(screen.getByRole('button', { name: 'Last 30 days' }));

  await waitFor(() => expect(api.summary).toHaveBeenLastCalledWith(lastDays(30)));
  expect(api.deliveriesPerDay).toHaveBeenLastCalledWith(lastDays(30));
});

test('a custom range is applied, and an invalid one is explained without a request', async () => {
  render(<OperationalReportsPage />);
  await screen.findByText('95.00%');
  const callsBefore = api.summary.mock.calls.length;

  fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-11-10' } });
  fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-11-01' } });
  await userEvent.click(screen.getByRole('button', { name: 'Apply' }));
  expect(screen.getByRole('alert')).toHaveTextContent('on or before');
  expect(api.summary).toHaveBeenCalledTimes(callsBefore);

  fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-11-01' } });
  fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-11-10' } });
  await userEvent.click(screen.getByRole('button', { name: 'Apply' }));

  await waitFor(() =>
    expect(api.summary).toHaveBeenLastCalledWith({ from: '2026-11-01', to: '2026-11-10' }),
  );
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('a failed load offers to try again', async () => {
  api.summary.mockRejectedValueOnce(new ApiRequestError('Unable to reach the server.'));
  render(<OperationalReportsPage />);

  expect(await screen.findByText('Unable to reach the server.')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findByText('95.00%')).toBeInTheDocument();
});
