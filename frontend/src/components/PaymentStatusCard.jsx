import React from 'react';

export function PaymentStatusCard({ paymentStatus, onCheckStatus, onResolve, loading }) {
  if (!paymentStatus) return null;

  const statusColors = {
    CAPTURED: 'bg-green-500/20 text-green-400 border-green-500/30',
    INITIATED: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
    FAILED: 'bg-red-500/20 text-red-400 border-red-500/30',
    DEFAULT: 'bg-slate-700 text-slate-300'
  };

  const borderBarColor = 
    paymentStatus.status === 'CAPTURED' ? 'bg-green-500' :
    paymentStatus.status === 'INITIATED' ? 'bg-yellow-500' :
    paymentStatus.status === 'FAILED' ? 'bg-red-500' : 'bg-slate-500';

  return (
    <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden transition-all duration-500">
      <div className={`absolute top-0 left-0 w-1 h-full ${borderBarColor}`}></div>
      <div className="pl-4">
        <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex justify-between items-center">
          Latest Attempt Status
          <button onClick={() => onCheckStatus(paymentStatus.id)} className="text-blue-400 hover:text-blue-300">
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </h3>

        <div className="grid grid-cols-2 gap-y-3 text-sm mb-5">
          <span className="text-slate-500">Attempt ID:</span>
          <span className="font-mono text-slate-300 text-right">{paymentStatus.id}</span>
          <span className="text-slate-500">Order ID:</span>
          <span className="font-mono text-blue-400 text-right truncate" title={paymentStatus.razorpayOrderId}>
            {paymentStatus.razorpayOrderId || 'N/A'}
          </span>
          <span className="text-slate-500 font-medium">Status:</span>
          <div className="text-right">
            <span className={`px-2.5 py-1 rounded-md text-xs font-bold inline-block border ${statusColors[paymentStatus.status] || statusColors.DEFAULT}`}>
              {paymentStatus.status}
            </span>
          </div>
        </div>

        {paymentStatus.status === 'INITIATED' && (
          <div className="pt-4 border-t border-slate-700 animate-pulse">
            <div className="flex items-start mb-4">
              <svg className="w-5 h-5 text-yellow-500 mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <p className="text-xs text-yellow-400/80">
                Payment stuck in INITIATED due to lost webhook acknowledgment.
              </p>
            </div>
            <button
              onClick={() => onResolve(paymentStatus.id)}
              disabled={loading}
              className="w-full py-2.5 bg-gradient-to-r from-yellow-600 to-orange-600 hover:from-yellow-500 hover:to-orange-500 text-white rounded-lg font-bold transition-all text-sm shadow-lg shadow-orange-900/30 flex items-center justify-center disabled:opacity-50"
            >
              <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              ⚡ Resolve Status (Self-Heal)
            </button>
          </div>
        )}
      </div>
    </div>
  );
}