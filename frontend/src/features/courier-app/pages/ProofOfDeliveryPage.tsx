import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function ProofOfDeliveryPage() {
  const { parcelId } = useParams();
  return (
    <ScreenPlaceholder
      title="Proof of Delivery"
      description="Recipient name and a photo or signature."
    >
      This screen is being built. (parcelId: {parcelId})
    </ScreenPlaceholder>
  );
}
