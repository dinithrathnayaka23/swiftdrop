import { CircleCheck, CircleX, Clock, Lock, Package, TriangleAlert } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import ConfirmDialog from '../../components/feedback/ConfirmDialog';
import LoadingState from '../../components/feedback/LoadingState';
import StateView from '../../components/feedback/StateView';
import Button from '../../components/ui/Button';

function Tile({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <p className="mb-2 text-sm font-semibold text-slate-700">{label}</p>
      <div className="rounded-2xl border border-slate-200 bg-white">{children}</div>
    </div>
  );
}

/** Dev-only gallery of the shared feedback states (/dev/ui). */
export default function UiStatesPage() {
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <h1 className="text-2xl font-bold text-slate-900">UI states</h1>
      <p className="mt-1 text-sm text-slate-500">Reusable feedback components. Development only.</p>

      <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <Tile label="Loading">
          <LoadingState />
        </Tile>
        <Tile label="Empty">
          <StateView
            icon={Package}
            title="No Parcels Found"
            message="You don't have any parcels yet."
            actions={<Button variant="outline">Track a Parcel</Button>}
          />
        </Tile>
        <Tile label="Success">
          <StateView
            icon={CircleCheck}
            tone="success"
            title="Success!"
            message="Your changes have been saved."
            actions={<Button variant="outline">Done</Button>}
          />
        </Tile>
        <Tile label="Failure">
          <StateView
            icon={CircleX}
            tone="danger"
            title="Action Failed"
            message="We couldn't complete this action. Please try again."
            actions={<Button variant="outline">Try Again</Button>}
          />
        </Tile>
        <Tile label="General error">
          <StateView
            icon={TriangleAlert}
            tone="danger"
            title="Something Went Wrong"
            message="An unexpected error occurred. Please try again later."
            actions={<Button variant="outline">Try Again</Button>}
          />
        </Tile>
        <Tile label="403 Unauthorized">
          <StateView
            icon={Lock}
            title="Access Denied"
            message="You don't have permission to access this page."
            actions={<Button variant="outline">Go Back</Button>}
          />
        </Tile>
        <Tile label="Session expired">
          <StateView
            icon={Clock}
            title="Session Expired"
            message="Your session has expired. Please sign in again to continue."
            actions={<Button variant="outline">Sign In</Button>}
          />
        </Tile>
        <Tile label="Confirmation dialog">
          <StateView
            icon={TriangleAlert}
            tone="warning"
            title="Delete Parcel?"
            message="Opens the confirmation dialog."
            actions={
              <Button variant="danger" onClick={() => setDialogOpen(true)}>
                Open Dialog
              </Button>
            }
          />
        </Tile>
      </div>

      <ConfirmDialog
        open={dialogOpen}
        title="Delete Parcel?"
        message="Are you sure you want to delete this parcel? This action cannot be undone."
        onConfirm={() => setDialogOpen(false)}
        onCancel={() => setDialogOpen(false)}
      />
    </div>
  );
}
