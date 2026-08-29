import React, { useState } from "react";
import { createSubscriptionApi } from "../services/api";

export default function SubscriptionForm({ onSubmitSuccess }) {
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    customerId: 101,
    amount: 1499.00,
    description: "Monthly Premium Plan",
    paymentDateTime: new Date(Date.now() + 10000).toISOString().slice(0, 16), // Default 10 sec in future
    timeSpan: 15
  });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    const payload = {
      customerId: Number(formData.customerId),
      amount: Number(formData.amount),
      description: formData.description,
      paymentDateTime: formData.paymentDateTime,
      timeSpan: Number(formData.timeSpan)
    };

    try {
      console.log(payload);
      const res = await createSubscriptionApi(payload);
      if (onSubmitSuccess) onSubmitSuccess(res.data);
    } catch (err) {
      console.error("Error creating subscription:", err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-lg bg-slate-900 border border-slate-800 p-6 rounded-2xl shadow-xl space-y-4 text-slate-100">
      <h3 className="text-lg font-bold text-white mb-2">Create New Subscription</h3>

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Customer ID</label>
        <input
          type="number"
          value={formData.customerId}
          onChange={(e) => setFormData({ ...formData, customerId: e.target.value })}
          className="w-full bg-slate-800 border border-slate-700 rounded-xl p-2.5 text-sm font-mono text-white focus:outline-none focus:border-emerald-500"
          required
        />
      </div>

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Plan Amount (₹)</label>
        <input
          type="number"
          step="0.01"
          value={formData.amount}
          onChange={(e) => setFormData({ ...formData, amount: e.target.value })}
          className="w-full bg-slate-800 border border-slate-700 rounded-xl p-2.5 text-sm font-mono text-white focus:outline-none focus:border-emerald-500"
          required
        />
      </div>

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Description</label>
        <input
          type="text"
          value={formData.description}
          onChange={(e) => setFormData({ ...formData, description: e.target.value })}
          className="w-full bg-slate-800 border border-slate-700 rounded-xl p-2.5 text-sm text-white focus:outline-none focus:border-emerald-500"
          placeholder="e.g. Pro SaaS Plan"
          required
        />
      </div>

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">First Charge Date & Time</label>
        <input
          type="datetime-local"
          value={formData.paymentDateTime}
          onChange={(e) => setFormData({ ...formData, paymentDateTime: e.target.value })}
          className="w-full bg-slate-800 border border-slate-700 rounded-xl p-2.5 text-sm text-white focus:outline-none focus:border-emerald-500"
          required
        />
      </div>

      <div>
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
          Interval
        </label>
        <select
          value={formData.timeSpan}
          onChange={(e) => setFormData({ ...formData, timeSpan: Number(e.target.value) })}
          className="w-full bg-slate-800 border border-slate-700 rounded-xl p-2.5 text-sm text-white focus:outline-none focus:border-emerald-500">
          <option value={10}>10 Seconds</option>
          <option value={15}>15 Seconds</option>
          <option value={20}>20 Seconds</option>
          <option value={30}>30 Seconds</option>
        </select>
      </div>


      <button
        type="submit"
        disabled={loading}
        className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 disabled:opacity-50 font-semibold text-sm rounded-xl text-white transition-colors"
      >
        {loading ? "Creating..." : "Save Subscription"}
      </button>
    </form>
  );
}