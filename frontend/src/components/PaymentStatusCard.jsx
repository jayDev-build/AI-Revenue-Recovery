import React from 'react';

export function PaymentStatusCard({ paymentStatus, onCheckStatus, loading }) {
    if (!paymentStatus) return null;

    // Configuration for status pill badges and left accent border line
    const statusConfig = {
        CAPTURED: { color: 'bg-emerald-500/20 text-emerald-400 border-emerald-500/30', bar: 'bg-emerald-500' },
        CREATED: { color: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30', bar: 'bg-yellow-500' },
        AUTHORIZED: { color: 'bg-blue-500/20 text-blue-400 border-blue-500/30', bar: 'bg-blue-500' },
        AMBIGUOUS: { color: 'bg-orange-500/20 text-orange-400 border-orange-500/30', bar: 'bg-orange-500' },
        FAILED: { color: 'bg-red-500/20 text-red-400 border-red-500/30', bar: 'bg-red-500' },
        REFUNDED: { color: 'bg-purple-500/20 text-purple-400 border-purple-500/30', bar: 'bg-purple-500' },
        DEFAULT: { color: 'bg-slate-700 text-slate-300 border-slate-600', bar: 'bg-slate-500' }
    };

    const currentConfig = statusConfig[paymentStatus.status] || statusConfig.DEFAULT;

    return (
        <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden transition-all duration-500">
            <div className={`absolute top-0 left-0 w-1 h-full ${currentConfig.bar}`}></div>
            <div className="pl-4">
                <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex justify-between items-center">
                    Latest Attempt Status
                    <button
                        onClick={() => onCheckStatus(paymentStatus.id)}
                        className="text-blue-400 hover:text-blue-300 transition-colors"
                        title="Refresh Status"
                    >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                        </svg>
                    </button>
                </h3>

                {paymentStatus.bankDegraded && (
                    <div className="mb-4 p-4 bg-amber-500/10 border border-amber-500/40 rounded-lg flex items-start animate-pulse">
                        <svg className="w-6 h-6 text-amber-400 mr-3 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                        </svg>
                        <div>
                            <h4 className="text-amber-400 font-bold text-sm">⚡ Server Intervention Active</h4>
                            <p className="text-amber-300 text-xs mt-1">
                                <strong>{paymentStatus.customerBank}</strong> was flagged as degraded by the recovery engine at the moment of payment.
                                Consider retrying with <strong>{paymentStatus.suggestedFallbackMethod || 'CARD'}</strong> for higher success probability.
                            </p>
                        </div>
                    </div>
                )}

                <div className="grid grid-cols-2 gap-y-3 text-sm mb-5">
                    <span className="text-slate-500">Attempt ID:</span>
                    <span className="font-mono text-slate-300 text-right">{paymentStatus.id}</span>
                    <span className="text-slate-500">Order ID:</span>
                    <span className="font-mono text-blue-400 text-right truncate" title={paymentStatus.razorpayOrderId}>
                        {paymentStatus.razorpayOrderId || 'N/A'}
                    </span>
                    <span className="text-slate-500">Bank:</span>
                    <span className="font-mono text-slate-300 text-right">{paymentStatus.customerBank || 'N/A'}</span>
                    <span className="text-slate-500">Amount:</span>
                    <span className="font-mono text-slate-300 text-right">
                        {paymentStatus.amount ? `₹${paymentStatus.amount}` : 'N/A'}
                    </span>
                    <span className="text-slate-500 font-medium">Status:</span>
                    <div className="text-right">
                        <span className={`px-2.5 py-1 rounded-md text-xs font-bold inline-block border ${currentConfig.color}`}>
                            {paymentStatus.status}
                        </span>
                    </div>
                </div>
            </div>
        </div>
    );
}