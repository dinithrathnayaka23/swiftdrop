import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function UpdateStatusPage() {
  const { parcelId } = useParams();
  return (
    <ScreenPlaceholder
      title="Update Status"
      description="Mark the delivery as picked up, in transit or out for delivery."
    >
      This screen is being built. (parcelId: {parcelId})
    </ScreenPlaceholder>
  );
}
