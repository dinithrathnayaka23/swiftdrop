import type { Role } from '../../types/auth';

/** The signed-in user's own profile (com.swiftdrop.user.dto.response.UserProfileResponse). */
export interface UserProfile {
  id: string;
  fullName: string;
  email: string;
  /** E.164, e.g. "+94771234567"; null for some Google sign-ups. */
  phone: string | null;
  role: Role;
  accountStatus: 'ACTIVE' | 'PENDING_APPROVAL' | 'SUSPENDED' | 'DEACTIVATED';
  createdAt: string;
}

/** What the user may change about themselves. */
export interface UpdateProfileRequest {
  fullName: string;
  /** E.164 */
  phone: string;
}
