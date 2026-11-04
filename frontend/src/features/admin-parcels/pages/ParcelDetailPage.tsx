import { ArrowLeft } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router';
import { PATHS } from '../../../routes/paths';
import AdminParcelPanel from '../components/AdminParcelPanel';

/**
 * One parcel on its own page: the same details as the panel beside the Parcels list. Used on
 * phones and tablets, and for direct links.
 */
export default function ParcelDetailPage() {
  const { parcelId = '' } = useParams();
  const navigate = useNavigate();

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <Link
        to={PATHS.adminParcels}
        className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Back to Parcels
      </Link>
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xs">
        <AdminParcelPanel parcelId={parcelId} onClose={() => navigate(PATHS.adminParcels)} />
      </div>
    </div>
  );
}
