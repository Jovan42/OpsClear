import type { ComponentProps } from 'react';
import { Link } from 'react-router-dom';

/** In-app links (`/guide/faq`, `/features`) navigate inside the SPA; other links open in a new tab. */
export function GuideAnchor({ href = '', children, ...rest }: ComponentProps<'a'>) {
  if (href.startsWith('/')) {
    return (
      <Link to={href} {...rest}>
        {children}
      </Link>
    );
  }
  if (href.startsWith('#')) {
    return (
      <a href={href} {...rest}>
        {children}
      </a>
    );
  }
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" {...rest}>
      {children}
    </a>
  );
}
