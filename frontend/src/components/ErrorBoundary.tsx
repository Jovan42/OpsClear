import { Component, type ReactNode } from 'react';
import i18n from '../i18n';

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
    const message = error instanceof Error ? error.message : i18n.t('shared2:errorPage.unexpectedError');
    return { hasError: true, message };
  }

  override render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex flex-col items-center justify-center gap-4 text-center px-6">
          <p className="text-gray-900 dark:text-gray-100 font-semibold">{i18n.t('shared2:errorPage.somethingWentWrong')}</p>
          <p className="text-gray-500 dark:text-gray-400 text-sm">{this.state.message}</p>
          <button
            onClick={() => window.location.reload()}
            className="text-sm text-brand hover:underline cursor-pointer"
          >
            {i18n.t('shared2:errorPage.reloadPage')}
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
