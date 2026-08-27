import React from 'react';

export function BankHealthGrid({ bankHealth, highlightedBanks }) {
  return (
    <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden">
      <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-purple-500 to-pink-500"></div>
      <h2 className="text-2xl font-bold mb-6 flex items-center">
        <svg className="w-6 h-6 mr-3 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
        Bank Health Grid
      </h2>

      <div className="space-y-4">
        {bankHealth.length === 0 ? (
          <p className="text-slate-500 text-center py-8 text-sm">No recent data. Initiate a payment or seed failures.</p>
        ) : (
          bankHealth.map((bank) => {
            const isDegraded = bank.successRate < 0.70;
            const pct = (bank.successRate * 100).toFixed(0);
            const isHighlighted = highlightedBanks[bank.bankName];

            return (
              <div
                key={bank.id}
                className={`p-4 rounded-xl border transition-all duration-700 ${
                  isHighlighted ? 'ring-2 ring-purple-500 scale-[1.02] shadow-[0_0_15px_rgba(168,85,247,0.5)]' : ''
                } ${isDegraded ? 'bg-red-900/10 border-red-500/30' : 'bg-slate-900/50 border-slate-700'}`}
              >
                <div className="flex justify-between items-center mb-2">
                  <h4 className="font-bold text-slate-200">{bank.bankName}</h4>
                  <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${isDegraded ? 'bg-red-500/20 text-red-400' : 'bg-green-500/20 text-green-400'}`}>
                    {isDegraded ? 'CRITICAL DIP' : 'NORMAL'}
                  </span>
                </div>

                <div className="mb-1 flex justify-between text-xs">
                  <span className="text-slate-500">Success Rate</span>
                  <span className={isDegraded ? 'text-red-400 font-mono font-bold' : 'text-green-400 font-mono font-bold'}>{pct}%</span>
                </div>
                <div className="w-full bg-slate-800 rounded-full h-1.5 mb-3 overflow-hidden">
                  <div className={`h-1.5 rounded-full ${isDegraded ? 'bg-red-500' : 'bg-green-500'}`} style={{ width: `${pct}%` }}></div>
                </div>

                {isDegraded && bank.aiSummary && (
                  <div className="mt-3 p-3 bg-slate-900 rounded-lg border border-purple-500/20 flex items-start">
                    <div className="w-5 h-5 rounded-full bg-purple-500/20 flex items-center justify-center mr-2 flex-shrink-0 mt-0.5">
                      <span className="text-xs">✨</span>
                    </div>
                    <p className="text-xs text-purple-200/80 leading-relaxed italic">
                      "{bank.aiSummary}"
                    </p>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}