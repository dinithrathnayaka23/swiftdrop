import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function OrderDetailsPage() {
  const { parcelId } = useParams();
  return (
    <ScreenPlaceholder title="Order Details" description="Order information and delivery status.">
      This screen is being built. (parcelId: {parcelId})
    </ScreenPlaceholder>
  );
}
