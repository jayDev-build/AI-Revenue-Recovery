import React from 'react';

export function PaymentStatusCard({ paymentStatus, onCheckStatus, onResolve, loading }) {
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

    // Resolve action is restricted strictly to CREATED, AUTHORIZED, and AMBIGUOUS
    const canResolve = ['CREATED', 'AUTHORIZED', 'AMBIGUOUS'].includes(paymentStatus.status);

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

                <div className="grid grid-cols-2 gap-y-3 text-sm mb-5">
                    <span className="text-slate-500">Attempt ID:</span>
                    <span className="font-mono text-slate-300 text-right">{paymentStatus.id}</span>
                    <span className="text-slate-500">Order ID:</span>
                    <span className="font-mono text-blue-400 text-right truncate" title={paymentStatus.razorpayOrderId}>
                        {paymentStatus.razorpayOrderId || 'N/A'}
                    </span>
                    <span className="text-slate-500 font-medium">Status:</span>
                    <div className="text-right">
                        <span className={`px-2.5 py-1 rounded-md text-xs font-bold inline-block border ${currentConfig.color}`}>
                            {paymentStatus.status}
                        </span>
                    </div>
                </div>

                {canResolve && (
                    <div className="pt-4 border-t border-slate-700">
                        <div className="flex items-start mb-4">
                            <svg className="w-5 h-5 text-amber-500 mr-2 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <p className="text-xs text-amber-400/90">
                                Transaction in <strong className="font-mono">{paymentStatus.status}</strong> state. Run direct Gateway reconciliation to sync ground truth.
                            </p>
                        </div>
                        <button
                            onClick={() => onResolve(paymentStatus.id)}
                            disabled={loading}
                            className="w-full py-2.5 bg-gradient-to-r from-amber-600 to-orange-600 hover:from-amber-500 hover:to-orange-500 text-white rounded-lg font-bold transition-all text-sm shadow-lg shadow-orange-900/30 flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {loading ? (
                                <span className="flex items-center">
                                    <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                    </svg>
                                    Resolving...
                                </span>
                            ) : (
                                <>
                                    <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                                    </svg>
                                    ⚡ Resolve Status (Self-Heal)
                                </>
                            )}
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}