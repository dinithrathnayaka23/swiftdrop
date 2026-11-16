import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import type { MerchantSubmittedState } from './MerchantSignupPage';
import MerchantSubmittedPage from './MerchantSubmittedPage';

function renderSubmitted(state: MerchantSubmittedState | null) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/signup/merchant/submitted', state }]}>
      <MerchantSubmittedPage />
    </MemoryRouter>,
  );
}

test('confirms the registration with the submitted details', () => {
  renderSubmitted({
    businessName: 'BrightMart',
    registrationNo: 'PV00234567',
    submittedAt: '2026-11-10T08:00:00Z',
  });

  expect(screen.getByRole('heading', { name: 'Registration Submitted' })).toBeInTheDocument();
  expect(screen.getByText('BrightMart')).toBeInTheDocument();
  expect(screen.getByText('PV00234567')).toBeInTheDocument();
  expect(screen.getByText('Submitted On')).toBeInTheDocument();
  expect(screen.getByText(/emailed you a link to confirm your email address/)).toBeInTheDocument();
});

test('opened directly (no submission), it still explains the next steps without details', () => {
  renderSubmitted(null);

  expect(screen.getByRole('heading', { name: 'Registration Submitted' })).toBeInTheDocument();
  expect(screen.queryByText('Business Name')).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Back to Home' })).toHaveAttribute('href', '/');
});
