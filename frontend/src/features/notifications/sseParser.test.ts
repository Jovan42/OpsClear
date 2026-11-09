import { describe, expect, it } from 'vitest';
import { createSseParser, type SseEvent } from './sseParser';

function parse(...chunks: string[]): SseEvent[] {
  const events: SseEvent[] = [];
  const parser = createSseParser((e) => events.push(e));
  chunks.forEach((c) => parser.push(c));
  return events;
}

describe('createSseParser', () => {
  it('dispatches a named event with its data on a blank line', () => {
    expect(parse('event:notification\ndata:{"id":"1"}\n\n')).toEqual([
      { event: 'notification', data: '{"id":"1"}' },
    ]);
  });

  it('strips one leading space after the colon', () => {
    expect(parse('event: read-state\ndata:  {"all":true}\n\n')).toEqual([
      { event: 'read-state', data: ' {"all":true}' },
    ]);
  });

  it('ignores comment lines such as the heartbeat', () => {
    expect(parse(':heartbeat\n\n', ':heartbeat\n\nevent:notification\ndata:x\n\n')).toEqual([
      { event: 'notification', data: 'x' },
    ]);
  });

  it('does not dispatch until the blank line arrives', () => {
    expect(parse('event:notification\ndata:x\n')).toEqual([]);
  });

  it('handles events and lines split across chunks', () => {
    expect(parse('eve', 'nt:notif', 'ication\nda', 'ta:{"id"', ':"1"}\n', '\n')).toEqual([
      { event: 'notification', data: '{"id":"1"}' },
    ]);
  });

  it('accepts CRLF and CR line endings, including a CRLF split across chunks', () => {
    expect(parse('event:a\r\ndata:1\r\n\r\n', 'event:b\rdata:2\r\r', 'event:c\r', '\ndata:3\r', '\n\r\n')).toEqual([
      { event: 'a', data: '1' },
      { event: 'b', data: '2' },
      { event: 'c', data: '3' },
    ]);
  });

  it('joins multiple data lines with a newline', () => {
    expect(parse('data:line 1\ndata:line 2\n\n')).toEqual([{ event: 'message', data: 'line 1\nline 2' }]);
  });

  it('defaults the event name to "message" and resets it after each event', () => {
    expect(parse('event:notification\ndata:a\n\ndata:b\n\n')).toEqual([
      { event: 'notification', data: 'a' },
      { event: 'message', data: 'b' },
    ]);
  });

  it('skips events without data, and ignores id, retry and unknown fields', () => {
    expect(parse('event:notification\n\nid:7\nretry:100\nfoo:bar\nevent:x\ndata:y\n\n')).toEqual([
      { event: 'x', data: 'y' },
    ]);
  });
});
