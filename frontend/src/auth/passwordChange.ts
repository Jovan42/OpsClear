import keycloak from './keycloak';

// JOB-404: changing the password runs Keycloak's own UPDATE_PASSWORD application-initiated
// action, so OpsClear never sees a password. Keycloak sends the browser back with
// `kc_action_status` (success, cancelled or error) in the redirect; keycloak-js reads it
// during init, reports it through `onActionUpdate` and strips it from the URL. AuthProvider
// registers `recordAccountAction` before init, and the Settings page reads the result once.

export const UPDATE_PASSWORD_ACTION = 'UPDATE_PASSWORD';

export type PasswordChangeStatus = 'success' | 'cancelled' | 'error';

let pendingStatus: PasswordChangeStatus | null = null;

/** `keycloak.onActionUpdate` handler: remembers the outcome of a password change. */
export function recordAccountAction(status: PasswordChangeStatus, action?: string): void {
  if (action === UPDATE_PASSWORD_ACTION) pendingStatus = status;
}

/** The outcome of the password change the user just came back from, if any. */
export function peekPasswordChangeStatus(): PasswordChangeStatus | null {
  return pendingStatus;
}

/** Forgets the outcome once the page has shown it, so it isn't shown again. */
export function clearPasswordChangeStatus(): void {
  pendingStatus = null;
}

/** Path Keycloak sends the user back to after the password change. */
export const PASSWORD_CHANGE_RETURN_PATH = '/settings';

/** Opens Keycloak's update-password page in `locale`, returning to Settings afterwards. */
export function startPasswordChange(locale: string): Promise<void> {
  return keycloak.login({
    action: UPDATE_PASSWORD_ACTION,
    redirectUri: `${window.location.origin}${PASSWORD_CHANGE_RETURN_PATH}`,
    locale,
  });
}
