import { deliveryForm } from './api';
import { fitWithin, MAX_PHOTO_EDGE } from './photo';

describe('fitWithin', () => {
  it('scales the longest edge down to 1600 px, keeping the shape', () => {
    expect(fitWithin(4032, 3024)).toEqual({ width: MAX_PHOTO_EDGE, height: 1200 });
    expect(fitWithin(3024, 4032)).toEqual({ width: 1200, height: MAX_PHOTO_EDGE });
  });

  it('never enlarges a small photo', () => {
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
  });

  it('keeps at least one pixel', () => {
    expect(fitWithin(10_000, 1)).toEqual({ width: MAX_PHOTO_EDGE, height: 1 });
  });
});

describe('deliveryForm', () => {
  it('sends the trimmed name, the note when there is one, and the photo as proof.jpg', () => {
    const photo = new Blob(['jpeg'], { type: 'image/jpeg' });
    const form = deliveryForm({ recipientName: ' Kasun Perera ', note: '  ', photo });

    expect(form.get('recipientName')).toBe('Kasun Perera');
    expect(form.has('note')).toBe(false);
    expect((form.get('photo') as File).name).toBe('proof.jpg');
  });

  it('leaves the photo out when there is none', () => {
    const form = deliveryForm({ recipientName: 'A', note: 'At the door' });

    expect(form.get('note')).toBe('At the door');
    expect(form.has('photo')).toBe(false);
  });
});
