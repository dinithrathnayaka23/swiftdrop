import { useParams } from 'react-router';
import ScreenPlaceholder from '../../../shared/ScreenPlaceholder';

export default function MerchantDetailPage() {
  const { merchantId } = useParams();
  return (
    <ScreenPlaceholder
      title="Merchant Details"
      description="Business details, approval status and history."
    >
      This screen is being built. (merchantId: {merchantId})
    </ScreenPlaceholder>
  );
}
