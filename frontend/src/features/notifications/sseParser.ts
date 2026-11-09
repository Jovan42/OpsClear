/** One dispatched Server-Sent Event. `event` defaults to "message", as in the spec. */
export interface SseEvent {
  event: string;
  data: string;
}

export interface SseParser {
  /** Feeds decoded text. Chunks may split lines (and `\r\n` pairs) anywhere. */
  push(chunk: string): void;
}

/**
 * A small parser for the `text/event-stream` format (the parts of the WHATWG spec the
 * notification stream uses). Lines end in `\n`, `\r\n` or `\r`. A blank line dispatches
 * the event built so far. Lines starting with `:` are comments (the server's heartbeat)
 * and are ignored. `data:` lines are joined with `\n`. An event with no `data:` line is
 * not dispatched. `id:` and `retry:` are ignored: the server doesn't send them (no
 * replay, and the client has its own reconnect policy).
 */
export function createSseParser(onEvent: (event: SseEvent) => void): SseParser {
  let buffer = '';
  let eventName = '';
  let dataLines: string[] = [];
  // A chunk ending in `\r` might be the first half of `\r\n`; the `\n` then starts the next
  // chunk and must not count as a second (blank) line.
  let skipLeadingLf = false;

  function processLine(line: string) {
    if (line === '') {
      if (dataLines.length > 0) {
        onEvent({ event: eventName || 'message', data: dataLines.join('\n') });
      }
      eventName = '';
      dataLines = [];
      return;
    }
    if (line.startsWith(':')) return;

    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);

    if (field === 'event') eventName = value;
    else if (field === 'data') dataLines.push(value);
  }

  return {
    push(chunk: string) {
      let text = chunk;
      if (skipLeadingLf && text.startsWith('\n')) text = text.slice(1);
      skipLeadingLf = false;
      buffer += text;

      let start = 0;
      for (let i = 0; i < buffer.length; i++) {
        const ch = buffer[i];
        if (ch !== '\n' && ch !== '\r') continue;
        processLine(buffer.slice(start, i));
        if (ch === '\r') {
          if (i + 1 < buffer.length) {
            if (buffer[i + 1] === '\n') i++;
          } else {
            skipLeadingLf = true;
          }
        }
        start = i + 1;
      }
      buffer = buffer.slice(start);
    },
  };
}
