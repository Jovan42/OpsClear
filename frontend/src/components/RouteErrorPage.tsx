import { useRouteError } from 'react-router-dom';

function getMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === 'string') return error;
  return 'An unexpected error occurred.';
}

export default function RouteErrorPage() {
  const error = useRouteError();
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-4 text-center px-6">
      <p className="text-gray-900 font-semibold">Something went wrong</p>
      <p className="text-gray-500 text-sm">{getMessage(error)}</p>
      <button
        onClick={() => window.location.reload()}
        className="text-sm text-brand hover:underline cursor-pointer"
      >
        Reload page
      </button>
    </div>
  );
}
