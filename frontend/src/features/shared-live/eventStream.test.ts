import { createEventStreamParser, type StreamMessage } from './eventStream';

describe('createEventStreamParser', () => {
  it('turns stream text into events, across chunk boundaries, skipping comments', () => {
    const messages: StreamMessage[] = [];
    const parse = createEventStreamParser((message) => messages.push(message));

    parse(':connected\n\nevent:sta');
    parse('tus\ndata:{"status":"PICKED_UP"}\r\n\r\n:ping\n\n');
    parse('data: first line\ndata: second line\n\n');

    expect(messages).toEqual([
      { event: 'status', data: '{"status":"PICKED_UP"}' },
      { event: 'message', data: 'first line\nsecond line' },
    ]);
  });
});
