import React from "react";

export default function SubscriptionTransactions({ transactions = [] }) {
  // Filter for captured transactions
  const successfulTxns = transactions.filter((t) => t.status === "CAPTURED");

  return (
    <div className="w-full max-w-4xl bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl text-slate-100">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-bold text-white">Successful Transactions</h3>
        <span className="text-xs font-mono text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-3 py-1 rounded-full">
          {successfulTxns.length} Captured
        </span>
      </div>

      {successfulTxns.length === 0 ? (
        <p className="text-sm text-slate-500 py-4 text-center">No successful transactions recorded yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-800/60 text-xs font-semibold uppercase text-slate-400 border-b border-slate-800">
              <tr>
                <th className="p-3">Txn ID</th>
                <th className="p-3">Order ID</th>
                <th className="p-3">Amount</th>
                <th className="p-3">Captured At</th>
                <th className="p-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/50">
              {successfulTxns.map((txn) => (
                <tr key={txn.id} className="hover:bg-slate-800/30">
                  <td className="p-3 font-mono text-xs text-white">#{txn.id}</td>
                  <td className="p-3 font-mono text-xs text-slate-400">{txn.razorpayOrderId || "N/A"}</td>
                  <td className="p-3 font-semibold text-emerald-400">₹{txn.amount?.toFixed(2)}</td>
                  <td className="p-3 text-xs text-slate-400">{txn.resolvedAt ? new Date(txn.resolvedAt).toLocaleString() : "N/A"}</td>
                  <td className="p-3">
                    <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-500/20 text-emerald-300">
                      CAPTURED
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}