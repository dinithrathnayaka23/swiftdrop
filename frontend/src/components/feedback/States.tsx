import { CircleCheck, CircleX, PackageOpen, TriangleAlert, type LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import Button from '../ui/Button';
import StateView from './StateView';

/**
 * Ready-made empty, success, failure and error states from the UI designs. Pass `action` for a
 * custom button (e.g. a ButtonLink), or `actionLabel` + `onAction` for a plain one.
 */
interface StateProps {
  title?: string;
  message?: ReactNode;
  icon?: LucideIcon;
  /** Replaces the default button. */
  action?: ReactNode;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
}

function actionButton(
  { action, actionLabel, onAction }: StateProps,
  defaultLabel: string,
  variant: 'primary' | 'outline',
) {
  if (action) return action;
  if (!onAction) return undefined;
  return (
    <Button variant={variant} onClick={onAction}>
      {actionLabel ?? defaultLabel}
    </Button>
  );
}

/** Nothing to show yet, e.g. "No Parcels Found". */
export function EmptyState(props: StateProps) {
  const { title = 'Nothing here yet', message, icon = PackageOpen, className } = props;
  return (
    <StateView
      icon={icon}
      tone="neutral"
      title={title}
      message={message}
      actions={actionButton(props, 'Refresh', 'outline')}
      className={className}
    />
  );
}

/** An action completed, e.g. "Your changes have been saved." */
export function SuccessState(props: StateProps) {
  const {
    title = 'Success!',
    message = 'Your changes have been saved.',
    icon = CircleCheck,
    className,
  } = props;
  return (
    <StateView
      icon={icon}
      tone="success"
      title={title}
      message={message}
      actions={actionButton(props, 'Done', 'outline')}
      className={className}
    />
  );
}

/** An action the user tried didn't work; they can try again. */
export function FailureState(props: StateProps) {
  const {
    title = 'Action Failed',
    message = "We couldn't complete this action. Please try again.",
    icon = CircleX,
    className,
  } = props;
  return (
    <StateView
      icon={icon}
      tone="danger"
      title={title}
      message={message}
      actions={actionButton(props, 'Try Again', 'outline')}
      className={className}
    />
  );
}

/** Data couldn't be loaded (server error, offline). */
export function ErrorState(props: StateProps) {
  const {
    title = 'Something Went Wrong',
    message = 'An unexpected error occurred. Please try again later.',
    icon = TriangleAlert,
    className,
  } = props;
  return (
    <StateView
      icon={icon}
      tone="danger"
      title={title}
      message={message}
      actions={actionButton(props, 'Try Again', 'outline')}
      className={className}
    />
  );
}
