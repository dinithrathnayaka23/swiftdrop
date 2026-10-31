/** Must match com.swiftdrop.common.security.Role and users.role on the backend. */
export const ROLES = ['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER_ADMIN'] as const;

export type Role = (typeof ROLES)[number];

export interface AuthUser {
  id: string;
  displayName: string;
  role: Role;
}

/**
 * The signed-in session as the page sees it. The refresh token is not here: it lives in an
 * httpOnly cookie that JavaScript can't read.
 */
export interface AuthSession {
  /** Short-lived access token (15 minutes), kept in memory only. */
  token: string;
  user: AuthUser;
}

// ---- API contracts (identity module, /api/v1/auth/**) ----

/** Which sign-in page the login comes from. Must match LoginPortal on the backend. */
export type LoginPortal = 'PUBLIC' | 'COURIER' | 'ADMIN';

export interface LoginRequest {
  email: string;
  password: string;
  /** Keep the user signed in for 30 days on this device (renewed on use). */
  rememberMe?: boolean;
  /** Defaults to the main page (PUBLIC) on the backend. */
  portal?: LoginPortal;
}

/** Returned by login, Google sign-in and refresh. */
export interface AuthResponse {
  token: string;
  /** Access token lifetime in seconds. */
  expiresIn: number;
  /** Whether the session should survive a browser restart. */
  rememberMe: boolean;
  user: AuthUser;
}

/** Sign in (or sign up as a customer) with the ID token from Google's button. */
export interface GoogleLoginRequest {
  credential: string;
  rememberMe?: boolean;
}

/** Whether the backend has a Google OAuth client ID, and that (public) ID. */
export interface GoogleConfig {
  enabled: boolean;
  clientId: string | null;
}

export interface CustomerRegistrationRequest {
  fullName: string;
  email: string;
  /** E.164 format, e.g. +94712345678 */
  phone: string;
  password: string;
}

/** Creates a MERCHANT user (contact person) and a PENDING merchant for admin approval. */
export interface MerchantRegistrationRequest {
  businessName: string;
  registrationNo: string;
  contactPersonName: string;
  email: string;
  phone: string;
  businessAddress: string;
  password: string;
}

export interface MerchantRegistrationResponse {
  merchantId: string;
  businessName: string;
  registrationNo: string;
  approvalStatus: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
  submittedAt: string;
}

/** Account created; a confirmation link was emailed. No session until it is opened. */
export interface CustomerRegistrationResponse {
  email: string;
}

/** The token comes from the emailed link (/verify-email?token=...). */
export interface EmailVerificationRequest {
  token: string;
}

export interface EmailVerifiedResponse {
  /** A merchant still needs admin approval before signing in. */
  awaitingApproval: boolean;
}

export interface ResendVerificationRequest {
  email: string;
}

export interface PasswordResetRequest {
  email: string;
}

/** The token comes from the emailed link (/reset-password?token=...). */
export interface PasswordResetConfirmRequest {
  token: string;
  newPassword: string;
}

export interface AuthApi {
  login(request: LoginRequest): Promise<AuthResponse>;
  /** Uses the session cookie. */
  refresh(): Promise<AuthResponse>;
  /** Ends this device's session on the server and deletes the cookie. */
  logout(): Promise<void>;
  googleConfig(): Promise<GoogleConfig>;
  loginWithGoogle(request: GoogleLoginRequest): Promise<AuthResponse>;
  registerCustomer(request: CustomerRegistrationRequest): Promise<CustomerRegistrationResponse>;
  confirmEmailVerification(request: EmailVerificationRequest): Promise<EmailVerifiedResponse>;
  /** Always succeeds (202) whether or not the email exists. */
  resendVerification(request: ResendVerificationRequest): Promise<void>;
  registerMerchant(request: MerchantRegistrationRequest): Promise<MerchantRegistrationResponse>;
  requestPasswordReset(request: PasswordResetRequest): Promise<void>;
  confirmPasswordReset(request: PasswordResetConfirmRequest): Promise<void>;
}
