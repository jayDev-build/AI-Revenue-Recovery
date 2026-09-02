import React from "react";

export default function SubscriptionList({ subscriptions = [], onStatusChange }) {
    const handleToggleStatus = async (id, currentStatus) => {
        const newStatus = currentStatus === "ACTIVE" ? "PAUSED" : "ACTIVE";
        try {
            await fetch(`/api/subscriptions/${id}/status?status=${newStatus}`, { method: "PATCH" });
            if (onStatusChange) onStatusChange(id, newStatus);
        } catch (err) {
            console.error("Failed to update status:", err);
        }
    };

    const getStatusBadge = (status) => {
        switch (status) {
            case "ACTIVE":
                return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">Active</span>;
            case "PAUSED":
                return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">Paused</span>;
            case "PENDING_ACTIVATION":
                return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-500/10 text-slate-400 border border-slate-500/20">Pending Activation</span>;
            default:
                return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">{status}</span>;
        }
    };

    return (
        <div className="w-full max-w-4xl bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl text-slate-100">
            <h3 className="text-lg font-bold text-white mb-4">Subscription Status Overview</h3>

            {subscriptions.length === 0 ? (
                <p className="text-sm text-slate-500 py-4 text-center">No subscriptions found.</p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm text-slate-300">
                        <thead className="bg-slate-800/60 text-xs font-semibold uppercase text-slate-400 border-b border-slate-800">
                            <tr>
                                <th className="p-3">ID</th>
                                <th className="p-3">Description</th>
                                <th className="p-3">Amount</th>
                                <th className="p-3">Next Charge</th>
                                <th className="p-3">Status</th>
                                <th className="p-3 text-right">Action</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50">
                            {subscriptions.map((sub) => (
                                <tr key={sub.id} className="hover:bg-slate-800/30">
                                    <td className="p-3 font-mono font-medium text-white">#{sub.id}</td>
                                    <td className="p-3 font-mono text-xs text-slate-400">{sub.description || "N/A"}</td>
                                    <td className="p-3 font-semibold text-white">₹{sub.planAmount?.toFixed(2)}</td>
                                    <td className="p-3 text-xs text-slate-400">{sub.nextChargeDate ? new Date(sub.nextChargeDate).toLocaleString() : "N/A"}</td>
                                    <td className="p-3">{getStatusBadge(sub.status)}</td>
                                    <td className="p-3 text-right">
                                        {sub.status !== "PENDING_ACTIVATION" && (
                                            <button
                                                onClick={() => handleToggleStatus(sub.id, sub.status)}
                                                className="px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-700 hover:bg-slate-800 transition-colors"
                                            >
                                                {sub.status === "ACTIVE" ? "Pause" : "Activate"}
                                            </button>
                                        )}
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
