import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function DeliveryDetailsPage() {
  const { parcelId } = useParams();
  return (
    <ScreenPlaceholder
      title="Delivery Details"
      description="Recipient, address and parcel information."
    >
      This screen is being built. (parcelId: {parcelId})
    </ScreenPlaceholder>
  );
}
