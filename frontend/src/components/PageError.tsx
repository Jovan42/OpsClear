interface PageErrorProps {
  readonly message: string;
  readonly onRetry?: () => void;
}

export default function PageError({ message, onRetry }: PageErrorProps) {
  return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-red-500 text-sm">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="text-sm text-brand hover:underline cursor-pointer"
        >
          Try again
        </button>
      )}
    </div>
  );
}
