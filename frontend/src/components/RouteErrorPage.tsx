import { useRouteError } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import i18n from '../i18n';

function getMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  return i18n.t('shared2:errorPage.unexpectedError');
}

export default function RouteErrorPage() {
  const { t } = useTranslation('shared2');
  const error = useRouteError();
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-4 text-center px-6">
      <p className="text-gray-900 dark:text-gray-100 font-semibold">{t('errorPage.somethingWentWrong')}</p>
      <p className="text-gray-500 dark:text-gray-400 text-sm">{getMessage(error)}</p>
      <button
        onClick={() => window.location.reload()}
        className="text-sm text-brand hover:underline cursor-pointer"
      >
        {t('errorPage.reloadPage')}
      </button>
    </div>
  );
}
