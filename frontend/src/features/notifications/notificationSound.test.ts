import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createNotificationSound, isActiveTab, SOUND_THROTTLE_MS } from './notificationSound';

const unread = { read: false };

let play: ReturnType<typeof vi.fn<() => void>>;
let enabled: boolean;
let active: boolean;
let time: number;

function sound() {
  return createNotificationSound({
    play,
    isEnabled: () => enabled,
    isActiveTab: () => active,
    now: () => time,
  });
}

beforeEach(() => {
  play = vi.fn<() => void>();
  enabled = true;
  active = true;
  time = 1_000_000;
});

describe('createNotificationSound', () => {
  it('plays for a new unread notification in the active tab', () => {
    expect(sound().onNotification(unread, true)).toBe(true);
    expect(play).toHaveBeenCalledOnce();
  });

  it('does not play in a hidden or unfocused tab', () => {
    active = false;
    expect(sound().onNotification(unread, true)).toBe(false);
    expect(play).not.toHaveBeenCalled();
  });

  it('does not play for a duplicate (already cached) notification', () => {
    expect(sound().onNotification(unread, false)).toBe(false);
    expect(play).not.toHaveBeenCalled();
  });

  it('does not play for a notification that is already read', () => {
    expect(sound().onNotification({ read: true }, true)).toBe(false);
    expect(play).not.toHaveBeenCalled();
  });

  it('does not play with the preference off', () => {
    enabled = false;
    expect(sound().onNotification(unread, true)).toBe(false);
    expect(play).not.toHaveBeenCalled();
  });

  it('plays once for a burst, then again after the throttle interval', () => {
    const s = sound();
    for (let i = 0; i < 10; i++) {
      s.onNotification(unread, true);
      time += 100;
    }
    expect(play).toHaveBeenCalledOnce();

    time += SOUND_THROTTLE_MS - 1000 - 1; // 3999ms after the first chime: still throttled
    expect(s.onNotification(unread, true)).toBe(false);
    time += 1;
    expect(s.onNotification(unread, true)).toBe(true);
    expect(play).toHaveBeenCalledTimes(2);
  });

  it('does not start the throttle when nothing was played', () => {
    const s = sound();
    active = false;
    s.onNotification(unread, true);
    active = true;
    time += 10;
    expect(s.onNotification(unread, true)).toBe(true);
  });
});

describe('isActiveTab', () => {
  const doc = (visibilityState: DocumentVisibilityState, focused: boolean) =>
    ({ visibilityState, hasFocus: () => focused }) as unknown as Document;

  it('needs the tab to be both visible and focused', () => {
    expect(isActiveTab(doc('visible', true))).toBe(true);
    expect(isActiveTab(doc('visible', false))).toBe(false);
    expect(isActiveTab(doc('hidden', true))).toBe(false);
  });
});
