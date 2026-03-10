import { useEffect } from 'react';

export function usePageTitle(...segments: (string | null | undefined)[]) {
  useEffect(() => {
    const parts = segments.filter(Boolean) as string[];
    document.title = [...parts, 'OpsClear'].join(' · ');
  });
}
