import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function ParcelDetailPage() {
  const { parcelId } = useParams();
  return (
    <ScreenPlaceholder
      title="Parcel Details"
      description="Parcel information, status history and receipt."
    >
      This screen is being built. (parcelId: {parcelId})
    </ScreenPlaceholder>
  );
}
