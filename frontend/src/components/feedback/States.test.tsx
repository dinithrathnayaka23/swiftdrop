import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EmptyState, ErrorState, FailureState, SuccessState } from './States';

describe('state components', () => {
  it('use the wording from the designs by default', () => {
    render(
      <>
        <SuccessState />
        <FailureState />
        <ErrorState />
      </>,
    );

    expect(screen.getByText('Success!')).toBeInTheDocument();
    expect(screen.getByText('Your changes have been saved.')).toBeInTheDocument();
    expect(screen.getByText('Action Failed')).toBeInTheDocument();
    expect(screen.getByText('Something Went Wrong')).toBeInTheDocument();
    // No handler, no button.
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('show a button that runs the handler', async () => {
    const retry = jest.fn();
    render(<FailureState onAction={retry} />);

    await userEvent.click(screen.getByRole('button', { name: 'Try Again' }));

    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('accept a custom title, message and action', () => {
    render(
      <EmptyState
        title="No Parcels Found"
        message="You don't have any parcels yet."
        action={<a href="/customer/book">Track a Parcel</a>}
      />,
    );

    expect(screen.getByText('No Parcels Found')).toBeInTheDocument();
    expect(screen.getByText("You don't have any parcels yet.")).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Track a Parcel' })).toBeInTheDocument();
  });
});
