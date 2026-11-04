import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function FailedDeliveryPage() {
  const { parcelId } = useParams();
  return (
    <ScreenPlaceholder
      title="Delivery Failed"
      description="Why the delivery could not be completed."
    >
      This screen is being built. (parcelId: {parcelId})
    </ScreenPlaceholder>
  );
}
