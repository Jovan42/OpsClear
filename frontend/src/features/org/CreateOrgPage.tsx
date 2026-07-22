import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import Button from '../../components/Button';
import { useCreateOrganisation } from './useOrganisation';
import { useCurrentOrg } from './OrgContext';
import { usePageTitle } from '../../hooks/usePageTitle';

export default function CreateOrgPage() {
  const { t } = useTranslation('org');
  usePageTitle(t('createOrgPageTitle'));
  const navigate = useNavigate();
  const { setOrg } = useCurrentOrg();
  const { mutate: createOrg, isPending } = useCreateOrganisation();
  const [apiError, setApiError] = useState<string | null>(null);

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().min(1, t('nameRequired')).max(100, t('maxNameLength')),
        slug: z
          .string()
          .min(2, t('slugLengthError'))
          .max(3, t('slugLengthError'))
          .regex(/^[A-Za-z]+$/, t('lettersOnly')),
      }),
    [t],
  );
  type FormValues = z.infer<typeof schema>;

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  function onSubmit(values: FormValues) {
    setApiError(null);
    createOrg(
      { name: values.name, slug: values.slug.toUpperCase() },
      {
        onSuccess: (data) => {
          setOrg(data);
          navigate('/org/settings');
        },
        onError: (err) => {
          if (isAxiosError(err) && err.response?.data?.message) {
            setApiError(err.response.data.message as string);
          } else {
            setApiError(t('somethingWentWrongTryAgain'));
          }
        },
      },
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">
            {t('createOrgHeading')}
          </h1>
          <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
            {t('createOrgSubtitle')}
          </p>
        </div>

        <div className="bg-white dark:bg-gray-800 rounded-2xl border border-gray-200 dark:border-gray-700 p-6 shadow-sm">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
            <div>
              <label
                htmlFor="org-name"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
              >
                {t('orgNameLabel')}
              </label>
              <input
                id="org-name"
                {...register('name')}
                placeholder={t('orgNamePlaceholder')}
                className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
              />
              {errors.name && (
                <p className="mt-1 text-xs text-red-600">{errors.name.message}</p>
              )}
            </div>

            <div>
              <label
                htmlFor="org-slug"
                className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1"
              >
                {t('slugLabel')}{' '}
                <span className="text-xs font-normal text-gray-400 dark:text-gray-500">
                  {t('slugHint')}
                </span>
              </label>
              <div className="flex items-center gap-2">
                <span className="inline-flex items-center px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 text-sm font-mono text-gray-500 dark:text-gray-400 select-none">
                  /
                </span>
                <input
                  id="org-slug"
                  {...register('slug')}
                  onChange={(e) => {
                    const upper = e.target.value.toUpperCase().replace(/[^A-Z]/g, '');
                    setValue('slug', upper, { shouldValidate: true });
                  }}
                  placeholder={t('slugPlaceholder')}
                  maxLength={3}
                  className="w-28 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm font-mono bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent uppercase"
                />
              </div>
              {errors.slug && (
                <p className="mt-1 text-xs text-red-600">{errors.slug.message}</p>
              )}
              {apiError && (
                <p className="mt-1 text-xs text-red-600">{apiError}</p>
              )}
            </div>

            <div className="pt-1">
              <Button type="submit" loading={isPending} style={{ width: '100%', justifyContent: 'center' }}>
                {t('createOrgButton')}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
