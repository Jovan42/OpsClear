/**
 * Domain events published by services after an action, and the listener that turns them into notifications
 * (ADR-0051). Events are handled after the triggering transaction commits, on the notification executor.
 */
package com.opsclear.event;
