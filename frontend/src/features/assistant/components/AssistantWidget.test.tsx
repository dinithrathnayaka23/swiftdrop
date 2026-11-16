import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { AssistantRateLimitError, assistantApi } from '../../../api/assistant';
import { ApiRequestError } from '../../../api/errors';
import type { AssistantChatResponse } from '../types';
import AssistantWidget from './AssistantWidget';

// Only assistantApi is replaced; AssistantRateLimitError stays the real class.
jest.mock('../../../api/assistant', () => ({
  ...jest.requireActual<typeof import('../../../api/assistant')>('../../../api/assistant'),
  assistantApi: { chat: jest.fn(), estimateFee: jest.fn() },
}));

const chat = jest.mocked(assistantApi.chat);
const estimateFee = jest.mocked(assistantApi.estimateFee);

const TRACKING_REPLY: AssistantChatResponse = {
  reply: 'SD7KQ2MX9TRB4H is in transit to Kandy.',
  cards: [
    {
      type: 'TRACKING',
      tracking: {
        trackingNumber: 'SD7KQ2MX9TRB4H',
        currentStatus: 'IN_TRANSIT',
        lastUpdatedAt: '2026-11-12T08:00:00Z',
        bookedAt: '2026-11-11T08:00:00Z',
        fromCity: 'Colombo',
        toCity: 'Kandy',
        weightKg: 2.5,
        history: [],
      },
    },
  ],
  suggestions: ['Where are my parcels?'],
  mode: 'AI',
};

function openWidget() {
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <AssistantWidget />
    </MemoryRouter>,
  );
  return {
    user,
    open: () => user.click(screen.getByRole('button', { name: 'Open the assistant' })),
  };
}

async function ask(user: ReturnType<typeof userEvent.setup>, question: string) {
  await user.type(screen.getByLabelText('Your message'), question);
  await user.click(screen.getByRole('button', { name: 'Send' }));
}

beforeEach(() => jest.clearAllMocks());

test('sends the conversation and shows the reply, its tracking card and new suggestions', async () => {
  chat.mockResolvedValueOnce(TRACKING_REPLY);
  const { user, open } = openWidget();
  await open();

  await ask(user, 'Where is SD7KQ2MX9TRB4H?');

  expect(chat).toHaveBeenCalledWith({
    messages: [{ role: 'user', content: 'Where is SD7KQ2MX9TRB4H?' }],
  });
  expect(await screen.findByText(/is in transit to Kandy/)).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Get live updates' })).toHaveAttribute(
    'href',
    '/track/SD7KQ2MX9TRB4H',
  );
  expect(screen.getByRole('button', { name: 'Where are my parcels?' })).toBeInTheDocument();
  expect(screen.queryByText('Basic mode')).not.toBeInTheDocument();
  expect(screen.getByText(/share personal details/)).toBeInTheDocument();
});

test('shows basic mode when the rules answered', async () => {
  chat.mockResolvedValueOnce({ ...TRACKING_REPLY, cards: [], mode: 'RULES' });
  const { user, open } = openWidget();
  await open();

  await ask(user, 'Hi');

  expect(await screen.findByText('Basic mode')).toBeInTheDocument();
  expect(screen.getByRole('status', { name: '' })).toHaveTextContent(/answers are simpler/);
});

test('the fee form checks its fields and adds the quote to the chat without the AI', async () => {
  estimateFee.mockResolvedValueOnce({
    cards: [{ type: 'FEE', fee: { totalFee: 750, currency: 'LKR', lines: [] } }],
    message: 'The delivery fee is LKR 750.00.',
  });
  const { user, open } = openWidget();
  await open();
  await user.click(screen.getByRole('button', { name: 'Estimate a fee' }));

  await user.click(screen.getByRole('button', { name: 'Get estimate' }));
  expect(screen.getByText('Enter a weight above 0')).toBeInTheDocument();
  expect(estimateFee).not.toHaveBeenCalled();

  await user.selectOptions(screen.getByLabelText('From district'), 'Colombo');
  await user.type(screen.getByLabelText('From city'), 'Colombo');
  await user.selectOptions(screen.getByLabelText('To district'), 'Kandy');
  await user.type(screen.getByLabelText('To city'), 'Kandy');
  await user.type(screen.getByLabelText('Weight (kg)'), '2');
  await user.click(screen.getByRole('button', { name: 'Get estimate' }));

  expect(estimateFee).toHaveBeenCalledWith(
    expect.objectContaining({ pickupDistrict: 'Colombo', dropoffCity: 'Kandy', weightKg: 2 }),
  );
  expect(await screen.findByText('The delivery fee is LKR 750.00.')).toBeInTheDocument();
  expect(screen.getByText('Estimated delivery fee')).toBeInTheDocument();
  expect(chat).not.toHaveBeenCalled();
});

test('a rate limit or maintenance blocks sending with a notice', async () => {
  chat.mockRejectedValueOnce(new AssistantRateLimitError('Too many questions.', 30));
  const { user, open } = openWidget();
  await open();

  await ask(user, 'Hi');

  expect(await screen.findByText(/ask again in 30 s/)).toBeInTheDocument();
  expect(screen.getByLabelText('Your message')).toBeDisabled();
});

test('maintenance shows its own notice', async () => {
  chat.mockRejectedValueOnce(
    new ApiRequestError('Maintenance', { status: 503, code: 'MAINTENANCE_MODE' }),
  );
  const { user, open } = openWidget();
  await open();

  await ask(user, 'Hi');

  const dialog = screen.getByRole('dialog', { name: 'SwiftDrop assistant' });
  expect(await within(dialog).findByText(/unavailable during maintenance/)).toBeInTheDocument();
  expect(screen.getByLabelText('Your message')).toBeDisabled();
});
