import { Fragment } from 'react';
import { generatePath, Link } from 'react-router';
import { PATHS } from '../../../routes/paths';

const TRACKING_NUMBER = /\b(SD[2-9A-HJKMNP-Z]{12})\b/g;

/** Plain text only (never HTML or markdown); tracking numbers become links to their tracking page. */
export default function MessageText({ text }: { text: string }) {
  const parts = text.split(TRACKING_NUMBER);
  return (
    <p className="break-words whitespace-pre-line">
      {parts.map((part, index) =>
        index % 2 === 1 ? (
          <Link
            key={index}
            to={generatePath(PATHS.trackResult, { trackingNumber: part })}
            className="font-semibold underline underline-offset-2"
          >
            {part}
          </Link>
        ) : (
          <Fragment key={index}>{part}</Fragment>
        ),
      )}
    </p>
  );
}
