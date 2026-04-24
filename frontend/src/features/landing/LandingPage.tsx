import { Navigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import keycloak from '../../auth/keycloak';

function Navbar() {
  return (
    <nav
      className="px-6 h-14 flex items-center justify-between shrink-0"
      style={{ backgroundColor: 'var(--brand)' }}
    >
      <span className="font-semibold text-lg tracking-tight text-white">OpsClear</span>
      <button
        onClick={() => keycloak.login()}
        className="text-sm text-white/80 hover:text-white transition-colors"
      >
        Log in
      </button>
    </nav>
  );
}

function Hero() {
  return (
    <section className="px-6 py-24 text-center">
      <div className="max-w-3xl mx-auto">
        <h1 className="text-4xl sm:text-5xl font-bold leading-tight mb-6" style={{ color: 'var(--brand)' }}>
          What's actually happening<br className="hidden sm:block" /> with your work today?
        </h1>
        <p className="text-xl text-gray-500 dark:text-gray-400 mb-10 max-w-xl mx-auto">
          One screen tells you where every job stands, who's blocked, and what needs your attention today.
          No status calls, no chasing updates. Just clarity.
        </p>
        <button
          onClick={() => keycloak.register()}
          className="px-8 py-3 rounded-lg text-white font-semibold text-lg hover:opacity-90 transition-opacity"
          style={{ backgroundColor: 'var(--brand)' }}
        >
          Get started
        </button>
      </div>
    </section>
  );
}

function ProblemStatement() {
  return (
    <section className="bg-gray-100 dark:bg-gray-800 px-6 py-20">
      <div className="max-w-2xl mx-auto text-center space-y-5">
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
          The status call is stealing your day
        </h2>
        <p className="text-gray-600 dark:text-gray-400 leading-relaxed">
          Every day, owners and managers spend hours chasing updates. Who's blocked? What's late?
          When will it be done? The answer is buried in messages, calls, and memory — not somewhere
          you can actually see it.
        </p>
        <p className="text-sm text-gray-500 dark:text-gray-500">
          Built for SME owners and operations managers who need the truth about their work, not another meeting.
        </p>
      </div>
    </section>
  );
}

function CTAFooter() {
  return (
    <footer className="px-6 py-20 text-center" style={{ backgroundColor: 'var(--brand)' }}>
      <h2 className="text-3xl font-bold text-white mb-3">Ready to get clarity?</h2>
      <p className="text-white/70 mb-8">Start now. No credit card required.</p>
      <button
        onClick={() => keycloak.register()}
        className="px-8 py-3 bg-white rounded-lg font-semibold text-lg hover:opacity-90 transition-opacity"
        style={{ color: 'var(--brand)' }}
      >
        Start now
      </button>
    </footer>
  );
}

export default function LandingPage() {
  const { authenticated } = useAuth();

  // TODO MIL-012: branch on subscription state → setup wall
  if (authenticated) return <Navigate to="/projects" replace />;

  return (
    <div className="min-h-screen flex flex-col">
      <Navbar />
      <main className="flex-1 bg-white dark:bg-gray-900">
        <Hero />
        <ProblemStatement />
        {/* Job 05: Features grid */}
        {/* Job 05: Add-ons overview */}
        {/* Job 04: Pricing calculator */}
      </main>
      <CTAFooter />
    </div>
  );
}
