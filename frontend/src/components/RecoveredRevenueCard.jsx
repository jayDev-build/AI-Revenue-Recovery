import React from 'react';
import { ShieldCheck } from 'lucide-react';


export default function RecoveredAmountCard({ amount = 0, userId = 1, handleResolveAmount }) {
    return (
        <div className="inline-flex items-center gap-4 rounded-2xl bg-slate-900 border border-slate-800 px-6 py-4 shadow-md text-slate-100">
            <div className="p-3 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex-shrink-0">
                <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
                <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Recovered Amount
                </span>
                <span>
                    <button
                        onClick={() => handleResolveAmount(userId)}
                        className="text-blue-400 hover:text-blue-300 transition-colors pl-10 width-fit"
                        title="Resolved Amount"
                    >
                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 20 20">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                        </svg>
                    </button>
                </span>
                <div className="text-3xl font-extrabold text-white tracking-tight mt-0.5">
                    ₹{Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </div>
            </div>
        </div>
    );
}