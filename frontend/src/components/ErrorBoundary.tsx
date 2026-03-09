import { Component, type ReactNode } from 'react';

interface Props {
  readonly children: ReactNode;
}

interface State {
  hasError: boolean;
  message: string;
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, message: '' };
  }

  static getDerivedStateFromError(error: unknown): State {
    const message = error instanceof Error ? error.message : 'An unexpected error occurred.';
    return { hasError: true, message };
  }

  override render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex flex-col items-center justify-center gap-4 text-center px-6">
          <p className="text-gray-900 font-semibold">Something went wrong</p>
          <p className="text-gray-500 text-sm">{this.state.message}</p>
          <button
            onClick={() => window.location.reload()}
            className="text-sm text-brand hover:underline cursor-pointer"
          >
            Reload page
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
