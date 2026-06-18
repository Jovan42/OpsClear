import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { schedulesApi, type ScheduleBody, type PauseBody } from '../../api/schedules';
import { useDebounce } from '../../hooks/useDebounce';

export function useSchedules(projectId: string) {
  return useQuery({
    queryKey: ['schedules', projectId],
    queryFn: () => schedulesApi.list(projectId),
    enabled: !!projectId,
  });
}

export function useCreateSchedule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ScheduleBody) => schedulesApi.create(projectId, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['schedules', projectId] }),
  });
}

export function useUpdateSchedule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ scheduleId, body }: { scheduleId: string; body: ScheduleBody }) =>
      schedulesApi.update(projectId, scheduleId, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['schedules', projectId] }),
  });
}

export function useDeleteSchedule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (scheduleId: string) => schedulesApi.delete(projectId, scheduleId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['schedules', projectId] }),
  });
}

export function usePauseSchedule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ scheduleId, body }: { scheduleId: string; body: PauseBody }) =>
      schedulesApi.pause(projectId, scheduleId, body),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['schedules', projectId] }),
  });
}

export function useResumeSchedule(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (scheduleId: string) => schedulesApi.resume(projectId, scheduleId),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['schedules', projectId] }),
  });
}

export function useSchedulePreview(cronExpression: string, timezone: string) {
  const debouncedCron = useDebounce(cronExpression, 500);
  return useQuery({
    queryKey: ['schedule-preview', debouncedCron, timezone],
    queryFn: () => schedulesApi.preview(debouncedCron, timezone),
    enabled: debouncedCron.trim().split(/\s+/).length === 6,
    retry: false,
  });
}
