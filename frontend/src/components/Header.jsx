import React from 'react';
import { useNavigate } from 'react-router-dom';

export function Header({ seedBank, setSeedBank, bankOptions, onSeedFailures, loading }) {
  const navigate = useNavigate();
  return (
    <header className="flex flex-col md:flex-row justify-between items-center pb-6 border-b border-slate-700 gap-4">
      <div>
        <div className="flex items-center space-x-4">
          <h1 className="text-4xl font-extrabold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent">
            AI Revenue Recovery
          </h1>
          <div className="flex items-center px-3 py-1.5 bg-green-500/10 border border-green-500/20 rounded-full shadow-[0_0_10px_rgba(34,197,94,0.2)]">
            <div className="w-2.5 h-2.5 rounded-full bg-green-500 animate-ping absolute"></div>
            <div className="w-2.5 h-2.5 rounded-full bg-green-500 mr-2 relative"></div>
            <span className="text-[11px] font-bold text-green-400 tracking-wider uppercase">Live Syncing</span>
          </div>
        </div>
        <p className="text-slate-400 mt-2">Flow 1: Payment Degradation & Lost Acknowledgment</p>
        <button
          onClick={() => navigate('/subscription')}
          className="mt-4 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-sm font-bold shadow-lg shadow-indigo-900/50 transition-all"
        >
          Manage Subscriptions &rarr;
        </button>
      </div>

      <div className="flex items-center space-x-3 bg-slate-800 p-3 rounded-xl border border-slate-700">
        <span className="text-sm font-bold text-slate-400">DEMO ACTIONS:</span>
        <select
          value={seedBank}
          onChange={(e) => setSeedBank(e.target.value)}
          className="bg-slate-900 border border-slate-600 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none"
        >
          {bankOptions.map((b) => (
            <option key={b} value={b}>{b}</option>
          ))}
        </select>
        <button
          onClick={onSeedFailures}
          disabled={loading}
          className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white rounded-lg text-sm font-bold shadow-lg shadow-red-900/50 transition-all disabled:opacity-50"
        >
          {loading ? 'Simulating...' : 'Simulate 30 Failures'}
        </button>
      </div>
    </header>
  );
}