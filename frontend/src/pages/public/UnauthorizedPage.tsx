import { Lock } from 'lucide-react';
import { useNavigate } from 'react-router';
import StateView from '../../components/feedback/StateView';
import Button from '../../components/ui/Button';
import ButtonLink from '../../components/ui/ButtonLink';
import { PATHS } from '../../routes/paths';

/** 403: signed in, but the role isn't allowed on that page. */
export default function UnauthorizedPage() {
  const navigate = useNavigate();
  return (
    <div className="flex min-h-[60vh] items-center justify-center px-4 py-12">
      <StateView
        icon={Lock}
        tone="neutral"
        title="Access Denied"
        message="You don't have permission to access this page."
        actions={
          <>
            <Button variant="outline" onClick={() => navigate(-1)}>
              Go Back
            </Button>
            <ButtonLink to={PATHS.home} variant="ghost">
              Go to Home
            </ButtonLink>
          </>
        }
      />
    </div>
  );
}
