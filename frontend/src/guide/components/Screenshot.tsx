import Zoom from 'react-medium-image-zoom';
import 'react-medium-image-zoom/dist/styles.css';

interface ScreenshotProps {
  src: string;
  /** Required: describes what the screenshot shows, for screen readers and when it fails to load. */
  alt: string;
  caption?: string;
}

/** A screenshot that zooms to full size on click. */
export function Screenshot({ src, alt, caption }: Readonly<ScreenshotProps>) {
  return (
    <figure className="not-prose my-6" data-testid="guide-screenshot">
      <Zoom>
        <img
          src={src}
          alt={alt}
          loading="lazy"
          className="w-full rounded-lg border border-gray-200 dark:border-gray-700 shadow-sm"
        />
      </Zoom>
      {caption && (
        <figcaption className="mt-2 text-center text-sm text-gray-500 dark:text-gray-400">{caption}</figcaption>
      )}
    </figure>
  );
}
