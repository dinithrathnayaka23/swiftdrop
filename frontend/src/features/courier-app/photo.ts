/** Longest edge of a proof photo, as the backend stores it. */
export const MAX_PHOTO_EDGE = 1600;
const JPEG_QUALITY = 0.85;

/** The size to draw at: at most `max` px on the longest edge, keeping the shape, never enlarged. */
export function fitWithin(
  width: number,
  height: number,
  max = MAX_PHOTO_EDGE,
): { width: number; height: number } {
  const factor = Math.min(1, max / Math.max(width, height));
  return {
    width: Math.max(1, Math.round(width * factor)),
    height: Math.max(1, Math.round(height * factor)),
  };
}

/** Decodes the photo upright: browsers apply the camera's EXIF rotation when asked. */
async function decode(file: Blob): Promise<CanvasImageSource & { width: number; height: number }> {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(file, { imageOrientation: 'from-image' });
  }
  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    return image;
  } finally {
    URL.revokeObjectURL(url);
  }
}

/**
 * Shrinks a camera photo before upload: at most 1600 px, re-encoded as JPEG. Phone photos are often
 * 4-12 MB; the result is usually a few hundred KB, so uploads are fast on mobile data and stay well
 * under the server's limit. Drawing onto a canvas also drops the EXIF data (such as GPS location).
 */
export async function resizePhoto(file: Blob): Promise<Blob> {
  const image = await decode(file);
  const size = fitWithin(image.width, image.height);
  const canvas = document.createElement('canvas');
  canvas.width = size.width;
  canvas.height = size.height;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('This browser cannot prepare the photo.');
  context.drawImage(image, 0, 0, size.width, size.height);
  if ('close' in image && typeof image.close === 'function') image.close();
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) =>
        blob ? resolve(blob) : reject(new Error('This browser cannot prepare the photo.')),
      'image/jpeg',
      JPEG_QUALITY,
    );
  });
}
