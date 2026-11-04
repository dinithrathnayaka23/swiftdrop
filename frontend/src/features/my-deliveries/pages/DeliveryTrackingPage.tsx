import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function DeliveryTrackingPage() {
  const { trackingNumber } = useParams();
  return (
    <ScreenPlaceholder
      title="Delivery Tracking"
      description="Live status, history and receipt for one of your parcels."
    >
      This screen is being built. (trackingNumber: {trackingNumber})
    </ScreenPlaceholder>
  );
}
