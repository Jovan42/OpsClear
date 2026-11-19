import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';

/**
 * JOB-399: router state key the invite page sets when it opens the projects list after
 * joining. The toast can't be shown from the invite page itself: the Toaster lives in
 * AppLayout, and a toast sent before it mounts is lost.
 */
export const JOINED_ORGANISATION_STATE = 'joinedOrganisation';

/** Call in AppLayout, below its Toaster: shows "You've joined <organisation>" once. */
export function useJoinedOrganisationToast() {
  const { t } = useTranslation('org');
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state as Record<string, unknown> | null;
  const organisation = state?.[JOINED_ORGANISATION_STATE];

  useEffect(() => {
    if (typeof organisation !== 'string') return;
    // A fixed id: StrictMode's second effect run updates the same toast instead of adding one
    toast.success(t('invite.joinedToast', { organisation }), { id: 'joined-organisation' });
    // Drop the state, so a refresh or Back doesn't show the toast again
    navigate(location.pathname + location.search, { replace: true, state: null });
  }, [organisation, t, navigate, location.pathname, location.search]);
}
