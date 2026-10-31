import { Signpost } from 'lucide-react';
import StateView from '../../components/feedback/StateView';
import ButtonLink from '../../components/ui/ButtonLink';
import { PATHS } from '../../routes/paths';

export default function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center px-4 py-12">
      <StateView
        illustration={
          <div className="relative flex size-28 items-center justify-center rounded-full bg-slate-100">
            <Signpost className="size-12 text-slate-400" strokeWidth={1.6} aria-hidden="true" />
            <span className="absolute -bottom-1 rounded-md bg-slate-700 px-2 py-0.5 text-sm font-bold text-white">
              404
            </span>
          </div>
        }
        title="Page Not Found"
        message="The page you are looking for doesn't exist or has been moved."
        actions={
          <ButtonLink to={PATHS.home} variant="outline">
            Go to Home
          </ButtonLink>
        }
      />
    </div>
  );
}
