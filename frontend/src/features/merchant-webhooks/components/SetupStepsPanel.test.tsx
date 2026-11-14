import { render, screen } from '@testing-library/react';
import SetupStepsPanel from './SetupStepsPanel';

describe('SetupStepsPanel', () => {
  it('renders numbered setup guidance, Swagger link and curl example', () => {
    render(<SetupStepsPanel />);

    expect(screen.getByRole('heading', { name: 'Set up your integration' })).toBeInTheDocument();
    expect(screen.getByText('Create an API key')).toBeInTheDocument();
    expect(screen.getByText('Configure a webhook')).toBeInTheDocument();
    expect(screen.getByText('Create an order')).toBeInTheDocument();
    expect(screen.getByText(/X-API-Key: your-api-key/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Swagger API documentation/ })).toHaveAttribute(
      'href',
      '/swagger-ui.html',
    );
    expect(screen.getByText(/Never expose your API key/)).toBeInTheDocument();
  });
});
