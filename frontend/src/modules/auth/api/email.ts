import { api } from '@/shared/api'
import { identityWrite } from '@/modules/system/api/identity'
export interface EmailProfile {email:string|null;verified:boolean;verifiedAt:string|null;pendingVerificationExpiresAt:string|null;deliveryStatus:string|null;deliveryErrorCode:string|null}
export interface VerificationReceipt {operationId:string;status:string;retryable:boolean;nextRetryAt:string|null;errorCode:string|null}
export const emailApi={
 profile:(signal?:AbortSignal)=>api.request<EmailProfile>('/account/email',{signal}),
 save:(email:string,signal?:AbortSignal)=>identityWrite<EmailProfile>('/account/email','PATCH',{email},signal),
 requestVerification:(signal?:AbortSignal)=>identityWrite<VerificationReceipt>('/account/email/verification','POST',undefined,signal),
 verify:(token:string,signal?:AbortSignal)=>identityWrite<EmailProfile>('/account/email/verify','POST',{token},signal),
}
