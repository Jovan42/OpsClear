import { useState } from 'react';
import type { ReactNode } from 'react';
import Button from '../components/Button';
import Modal from '../components/Modal';
import StatusBadge from '../components/StatusBadge';
import RoleBadge from '../components/RoleBadge';
import { usePreferences } from '../hooks/usePreferences';
import type { JobStatus } from '../types';

const jobStatuses: JobStatus[] = ['NEW', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'];
const roles = ['OWNER', 'ADMIN', 'MEMBER'] as const;

const brandOptions = [
  { label: 'Indigo', value: '#4f46e5', dark: '#4338ca', light: '#e0e7ff' },
  { label: 'Blue',   value: '#2563eb', dark: '#1d4ed8', light: '#dbeafe' },
  { label: 'Emerald', value: '#059669', dark: '#047857', light: '#d1fae5' },
] as const;

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mb-12">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-gray-400 dark:text-gray-500 mb-4 pb-2 border-b border-gray-200 dark:border-gray-700">
        {title}
      </h2>
      {children}
    </section>
  );
}

function Swatch({ color, label, className }: { color: string; label: string; className: string }) {
  return (
    <div className="flex flex-col items-center gap-1.5">
      <div className={`w-14 h-14 rounded-xl border border-black/5 ${className}`} />
      <span className="text-xs text-gray-500 dark:text-gray-400 text-center leading-tight">{label}</span>
      <span className="text-xs font-mono text-gray-400 dark:text-gray-500">{color}</span>
    </div>
  );
}

export default function DesignPage() {
  const [modalOpen, setModalOpen] = useState(false);
  const [activeBrand, setActiveBrand] = useState(0);
  const { prefs, update } = usePreferences();

  function applyBrand(idx: number) {
    const b = brandOptions[idx];
    document.documentElement.style.setProperty('--brand', b.value);
    document.documentElement.style.setProperty('--brand-dark', b.dark);
    document.documentElement.style.setProperty('--brand-light', b.light);
    setActiveBrand(idx);
  }

  return (
    <div className="max-w-3xl mx-auto px-6 py-10">
      <div className="mb-10">
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">Design System</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">Component gallery for OpsClear UI review.</p>
      </div>

      {/* ── Dark mode ── */}
      <Section title="Dark mode — theme toggle">
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          Current theme: <span className="font-medium text-gray-900 dark:text-gray-100">{prefs.theme}</span>
        </p>
        <div className="flex rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden w-fit">
          {(['light', 'dark', 'system'] as const).map((t) => (
            <button
              key={t}
              onClick={() => update({ theme: t })}
              className={`px-4 py-2 text-sm font-medium transition-colors cursor-pointer capitalize ${
                prefs.theme === t
                  ? 'bg-gray-900 text-white dark:bg-white dark:text-gray-900'
                  : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
              }`}
            >
              {t}
            </button>
          ))}
        </div>
        <p className="text-xs text-gray-400 dark:text-gray-500 mt-3">
          Light / Dark force the theme. System follows <code className="font-mono">prefers-color-scheme</code>.
          Preference persists in <code className="font-mono">localStorage</code>.
        </p>
      </Section>

      {/* ── Brand colour switcher ── */}
      <Section title="Brand colour — live switcher">
        <div className="flex gap-3">
          {brandOptions.map((b, i) => (
            <button
              key={b.label}
              onClick={() => applyBrand(i)}
              className={`px-4 py-2 rounded-lg text-sm font-medium border transition-all cursor-pointer ${
                activeBrand === i
                  ? 'border-2 shadow-sm text-white'
                  : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:border-gray-300 bg-white dark:bg-gray-800'
              }`}
              style={activeBrand === i ? { backgroundColor: b.value, borderColor: b.dark } : {}}
            >
              {b.label}
            </button>
          ))}
        </div>
        <p className="text-xs text-gray-400 dark:text-gray-500 mt-3">
          Switches live — updates navbar, buttons, badges, and all brand tokens instantly.
        </p>
      </Section>

      {/* ── Colour Palette ── */}
      <Section title="Colour palette">
        <div className="space-y-6">
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-3">Brand tokens</p>
            <div className="flex flex-wrap gap-4">
              <Swatch color="--brand" label="Brand" className="bg-brand" />
              <Swatch color="--brand-dark" label="Brand dark" className="bg-brand-dark" />
              <Swatch color="--brand-light" label="Brand light" className="bg-brand-light" />
            </div>
          </div>
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-3">Job status</p>
            <div className="flex flex-wrap gap-4">
              <Swatch color="gray-100/600" label="New" className="bg-gray-100" />
              <Swatch color="blue-100/700" label="In Progress" className="bg-blue-100" />
              <Swatch color="red-100/700" label="Blocked" className="bg-red-100" />
              <Swatch color="green-100/700" label="Completed" className="bg-green-100" />
            </div>
          </div>
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-3">Grays</p>
            <div className="flex flex-wrap gap-4">
              {(['gray-50','gray-100','gray-200','gray-300','gray-400','gray-500','gray-700','gray-900'] as const).map((g) => (
                <Swatch key={g} color={g} label={g} className={`bg-${g}`} />
              ))}
            </div>
          </div>
        </div>
      </Section>

      {/* ── Typography ── */}
      <Section title="Typography — Inter">
        <div className="space-y-3">
          <p className="text-2xl font-semibold text-gray-900 dark:text-gray-100">Heading 1 — 2xl / semibold</p>
          <p className="text-xl font-semibold text-gray-900 dark:text-gray-100">Heading 2 — xl / semibold</p>
          <p className="text-base font-medium text-gray-900 dark:text-gray-100">Heading 3 — base / medium</p>
          <p className="text-sm text-gray-700 dark:text-gray-300">Body — sm / regular — The quick brown fox jumps over the lazy dog.</p>
          <p className="text-sm text-gray-500 dark:text-gray-400">Body muted — sm / regular / gray-500</p>
          <p className="text-xs text-gray-400 dark:text-gray-500">Caption — xs / regular / gray-400</p>
          <p className="text-xs font-mono text-gray-500 dark:text-gray-400">Mono — xs / mono — UUID: 3f2d1a...</p>
        </div>
      </Section>

      {/* ── Buttons ── */}
      <Section title="Buttons">
        <div className="space-y-4">
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-2">Variants — md</p>
            <div className="flex flex-wrap gap-3">
              <Button variant="primary">Primary</Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="danger">Danger</Button>
              <Button variant="ghost">Ghost</Button>
            </div>
          </div>
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-2">Sizes</p>
            <div className="flex flex-wrap items-center gap-3">
              <Button size="sm">Small</Button>
              <Button size="md">Medium</Button>
            </div>
          </div>
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-2">States</p>
            <div className="flex flex-wrap gap-3">
              <Button loading>Loading</Button>
              <Button disabled>Disabled</Button>
            </div>
          </div>
        </div>
      </Section>

      {/* ── Badges ── */}
      <Section title="Badges">
        <div className="space-y-4">
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-2">Job status</p>
            <div className="flex flex-wrap gap-2">
              {jobStatuses.map((s) => <StatusBadge key={s} status={s} />)}
            </div>
          </div>
          <div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mb-2">Project role</p>
            <div className="flex flex-wrap gap-2">
              {roles.map((role) => <RoleBadge key={role} role={role} />)}
            </div>
          </div>
        </div>
      </Section>

      {/* ── Form elements ── */}
      <Section title="Form elements">
        <div className="max-w-sm space-y-4">
          <div>
            <label htmlFor="input-default" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Input — default
            </label>
            <input
              id="input-default"
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent"
              placeholder="Placeholder text"
            />
          </div>
          <div>
            <label htmlFor="input-error" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Input — error
            </label>
            <input
              id="input-error"
              className="w-full rounded-lg border border-red-400 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-red-400 focus:border-transparent"
              placeholder="Invalid value"
            />
            <p className="mt-1 text-xs text-red-600">This field is required</p>
          </div>
          <div>
            <label htmlFor="textarea-default" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
              Textarea
            </label>
            <textarea
              id="textarea-default"
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder:text-gray-400 dark:placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:border-transparent resize-none"
              placeholder="Enter a description…"
              rows={3}
            />
          </div>
        </div>
      </Section>

      {/* ── Cards ── */}
      <Section title="Cards — project">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-xl">
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 hover:border-gray-300 dark:hover:border-gray-600 hover:shadow-sm transition-all">
            <div className="flex items-start justify-between gap-2 mb-2">
              <h3 className="font-semibold text-gray-900 dark:text-gray-100 text-sm">Website Redesign</h3>
              <span className="shrink-0 text-xs font-medium px-2 py-0.5 rounded-full bg-brand-light text-brand dark:bg-green-900/40 dark:text-green-300">
                Owner
              </span>
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400 line-clamp-2 leading-relaxed">
              Full overhaul of the company website — new branding, mobile-first layout.
            </p>
          </div>
          <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl p-5 hover:border-gray-300 dark:hover:border-gray-600 hover:shadow-sm transition-all">
            <div className="flex items-start justify-between gap-2 mb-2">
              <h3 className="font-semibold text-gray-900 dark:text-gray-100 text-sm">Office Relocation</h3>
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400 line-clamp-2 leading-relaxed italic">
              No description
            </p>
          </div>
        </div>
      </Section>

      {/* ── Modal ── */}
      <Section title="Modal">
        <Button onClick={() => setModalOpen(true)}>Open example modal</Button>
        <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Example Modal">
          <div className="space-y-3">
            <p className="text-sm text-gray-600 dark:text-gray-300">
              This is a modal dialog. Click the × button or the backdrop to close it.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button onClick={() => setModalOpen(false)}>Confirm</Button>
            </div>
          </div>
        </Modal>
      </Section>
    </div>
  );
}
