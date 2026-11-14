import { render, screen } from '@testing-library/react';
import IntegrationStatusPanel from './IntegrationStatusPanel';

describe('IntegrationStatusPanel', () => {
  it('shows integration states and the last successful delivery', () => {
    render(
      <IntegrationStatusPanel
        apiKeyActive
        webhookConfigured
        lastSuccessfulDelivery={{
          eventType: 'parcel.status.changed',
          trackingNumber: 'SD123456789',
          deliveredAt: '14 Nov 2026, 12:00 AM',
        }}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Integration status' })).toBeInTheDocument();
    expect(screen.getAllByText('Active')).toHaveLength(2);
    expect(screen.getByText('parcel.status.changed · SD123456789')).toBeInTheDocument();
  });

  it('explains when setup or delivery is incomplete', () => {
    render(
      <IntegrationStatusPanel
        apiKeyActive={false}
        webhookConfigured={false}
        lastSuccessfulDelivery={null}
      />,
    );

    expect(screen.getAllByText('Not configured')).toHaveLength(2);
    expect(screen.getByText('No successful deliveries yet.')).toBeInTheDocument();
  });
});
