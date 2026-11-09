// When a live notification plays a sound (JOB-282). The audio itself is notificationChime.ts.

/** At most one chime per this interval, so a burst of notifications plays once. */
export const SOUND_THROTTLE_MS = 4_000;

export interface NotificationSoundDeps {
  /** Plays the chime (the audio layer; mocked in tests). */
  play: () => void;
  /** The user's "Notification sound" preference. */
  isEnabled: () => boolean;
  /** Whether this tab is the one the user is using; see {@link isActiveTab}. */
  isActiveTab: () => boolean;
  now?: () => number;
}

/**
 * Visible and focused. Every open tab gets the same event, so this keeps three tabs from
 * chiming three times. With no tab focused (e.g. the user is in another app), none plays.
 */
export function isActiveTab(doc: Document = document): boolean {
  return doc.visibilityState === 'visible' && doc.hasFocus();
}

/**
 * Decides whether a `notification` event from the stream plays the chime. Only new
 * (`isNew`: not already in the cache) unread notifications do, in the active tab, with
 * the preference on, and not within {@link SOUND_THROTTLE_MS} of the last chime. The
 * refetch on (re)connect and `read-state` events never come through here.
 */
export function createNotificationSound({ play, isEnabled, isActiveTab, now = Date.now }: NotificationSoundDeps) {
  let lastPlayedAt = Number.NEGATIVE_INFINITY;

  return {
    /** Returns whether the chime was played. */
    onNotification(notification: { read: boolean }, isNew: boolean): boolean {
      if (!isNew || notification.read) return false;
      if (!isEnabled() || !isActiveTab()) return false;
      const time = now();
      if (time - lastPlayedAt < SOUND_THROTTLE_MS) return false;
      lastPlayedAt = time;
      play();
      return true;
    },
  };
}
