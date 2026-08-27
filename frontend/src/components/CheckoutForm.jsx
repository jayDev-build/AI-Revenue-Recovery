import React from 'react';

export function CheckoutForm({
  userId,
  setUserId,
  amount,
  setAmount,
  checkoutBank,
  setCheckoutBank,
  simulateDrop,
  setSimulateDrop,
  bankOptions,
  isSelectedBankDegraded,
  selectedBankSnapshot,
  onSubmit,
  loading
}) {
  return (
    <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden">
      <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-blue-500 to-purple-500"></div>
      <h2 className="text-2xl font-bold mb-6 flex items-center">
        <svg className="w-6 h-6 mr-3 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
        </svg>
        Checkout Terminal
      </h2>

      {isSelectedBankDegraded && (
        <div className="mb-6 p-4 bg-orange-500/10 border border-orange-500/50 rounded-lg flex items-start">
          <svg className="w-6 h-6 text-orange-400 mr-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <div>
            <h4 className="text-orange-400 font-bold text-sm">⚠️ Degraded Success Rate</h4>
            <p className="text-orange-300 text-xs mt-1">
              {checkoutBank} is currently experiencing a low success rate ({(selectedBankSnapshot.successRate * 100).toFixed(0)}%). Consider using an alternate method.
            </p>
          </div>
        </div>
      )}

      <form onSubmit={onSubmit} className="space-y-5">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-slate-400 mb-1">User ID</label>
            <input
              type="number"
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-400 mb-1">Amount (INR)</label>
            <input
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-slate-400 mb-1">Select Bank</label>
          <select
            value={checkoutBank}
            onChange={(e) => setCheckoutBank(e.target.value)}
            className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100 focus:outline-none focus:border-blue-500"
          >
            {bankOptions.map((b) => <option key={b} value={b}>{b}</option>)}
          </select>
        </div>

        <div className="flex items-center p-3 bg-slate-900/50 rounded-lg border border-slate-700">
          <input
            type="checkbox"
            id="simulateDrop"
            checked={simulateDrop}
            onChange={(e) => setSimulateDrop(e.target.checked)}
            className="w-4 h-4 text-purple-600 bg-slate-900 border-slate-600 rounded focus:ring-purple-500 focus:ring-2"
          />
          <label htmlFor="simulateDrop" className="ml-3 text-sm font-medium text-slate-300">
            Simulate Network Drop (Lost Ack on Webhook)
          </label>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-lg font-bold transition-all shadow-lg shadow-blue-900/50 disabled:opacity-50"
        >
          {loading ? 'Processing...' : 'Pay Now'}
        </button>
      </form>
    </div>
  );
}