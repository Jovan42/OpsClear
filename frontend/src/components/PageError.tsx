import { useTranslation } from 'react-i18next';

interface PageErrorProps {
  readonly message: string;
  readonly onRetry?: () => void;
}

export default function PageError({ message, onRetry }: PageErrorProps) {
  const { t } = useTranslation('shared2');
  return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-red-500 text-sm">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="text-sm text-brand hover:underline cursor-pointer"
        >
          {t('pageError.tryAgain')}
        </button>
      )}
    </div>
  );
}
