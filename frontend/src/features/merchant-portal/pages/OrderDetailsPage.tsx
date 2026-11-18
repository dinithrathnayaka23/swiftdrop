import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function OrderDetailsPage() {
  const { trackingNumber } = useParams();
  return (
    <ScreenPlaceholder title="Order Details" description="Order information and delivery status.">
      This screen is being built. (tracking number: {trackingNumber})
    </ScreenPlaceholder>
  );
}
