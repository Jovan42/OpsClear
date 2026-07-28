import type { JobTypeColor } from '../types';
import { JOB_TYPE_BADGE_CLASSES } from '../utils/jobTypeColors';

interface JobTypeBadgeProps {
  name: string;
  color: JobTypeColor;
}

export default function JobTypeBadge({ name, color }: JobTypeBadgeProps) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${JOB_TYPE_BADGE_CLASSES[color]}`}>
      {name}
    </span>
  );
}
