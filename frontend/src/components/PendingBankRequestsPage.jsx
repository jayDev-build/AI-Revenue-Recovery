import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { getAllPendingBankRequests } from "../services/api";

const BANK_RESPONSES = [
    { code: "SUCCESS", label: "200 - Success / Approved" },
    { code: "INSUFFICIENT_FUNDS", label: "401 - Insufficient Funds" },
    { code: "EXPIRED_CARD", label: "402 - Expired Card" },
    { code: "BANK_UNRESPONSIVE", label: "503 - Bank Server Unresponsive" },
    { code: "INVALID_PIN", label: "403 - Invalid PIN / OTP" },
    { code: "CARD_BLOCKED", label: "405 - Card Blocked by Issuer" },
    { code: "GATEWAY_TIMEOUT", label: "504 - Gateway Timed Out" }
];

export default function PendingBankRequestsPage() {
    const navigate = useNavigate();
    const [pendingRequests, setPendingRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedResponses, setSelectedResponses] = useState({});
    const [submittingIds, setSubmittingIds] = useState({});

    const fetchPendingRequests = async () => {
        setLoading(true);
        try {
            const res = await getAllPendingBankRequests();
            console.log(res);
            if (res.status === 200) {
                const data = await res.data;
                setPendingRequests(data);

                const defaults = {};
                data.forEach((req) => {
                    defaults[req.id] = "SUCCESS";
                });
                setSelectedResponses(defaults);
            }
        } catch (err) {
            console.error("Failed to load pending bank requests:", err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPendingRequests();
    }, []);

    const handleResponseChange = (attemptId, code) => {
        setSelectedResponses((prev) => ({ ...prev, [attemptId]: code }));
    };

    const handleSendResponse = async (item) => {
        const responseCode = selectedResponses[item.id] || "SUCCESS";
        setSubmittingIds((prev) => ({ ...prev, [item.id]: true }));

        const payload = {
            subscriptionId: item.subscription ? item.subscription.id : null,
            razorpayOrderId: item.razorpayOrderId,
            bankTransactionId: "BANK_TXN_" + Math.floor(100000 + Math.random() * 900000),
            amount: item.amount,
            responseCode: responseCode,
            message: `Manual simulation decision: ${responseCode}`
        };

        try {
            const res = await fetch("/api/bank/callback", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            if (res.status === 200) {
                setPendingRequests((prev) => prev.filter((req) => req.id !== item.id));
            } else {
                alert(`Failed to send response for Order #${item.razorpayOrderId}`);
            }
        } catch (err) {
            console.error("Error sending callback:", err);
        } finally {
            setSubmittingIds((prev) => ({ ...prev, [item.id]: false }));
        }
    };

    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10 space-y-6">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
                <div>
                    <button
                        onClick={() => navigate("/")}
                        className="text-xs text-slate-400 hover:text-white transition-colors"
                    >
                        ← Back to Dashboard
                    </button>
                    <h1 className="text-2xl font-bold text-white tracking-tight mt-1">
                        Pending Bank Authorizations
                    </h1>
                </div>

                <button
                    onClick={fetchPendingRequests}
                    className="px-4 py-2 bg-slate-900 border border-slate-700 hover:bg-slate-800 rounded-xl text-xs font-semibold text-slate-300 transition-colors"
                >
                    Refresh Pending List ({pendingRequests.length})
                </button>
            </div>

            {loading ? (
                <div className="p-12 text-center text-slate-400">Loading pending requests...</div>
            ) : pendingRequests.length === 0 ? (
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-12 text-center text-slate-400">
                    No payment attempts currently pending bank authorization.
                </div>
            ) : (
                <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl overflow-x-auto">
                    <table className="w-full text-left text-sm text-slate-300">
                        <thead className="bg-slate-800/60 text-xs font-semibold uppercase text-slate-400 border-b border-slate-800">
                            <tr>
                                <th className="p-3">Attempt / Order ID</th>
                                <th className="p-3">Customer ID</th>
                                <th className="p-3">Subscription Details</th>
                                <th className="p-3">Amount</th>
                                <th className="p-3">Initiated At</th>
                                <th className="p-3">Select Bank Response</th>
                                <th className="p-3 text-right">Action</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-800/50">
                            {pendingRequests.map((item) => {
                                const isSubmitting = submittingIds[item.id];
                                return (
                                    <tr key={item.id} className="hover:bg-slate-800/30">
                                        <td className="p-3">
                                            <div className="font-mono text-xs text-white font-medium">#{item.id}</div>
                                            <div className="text-[11px] text-slate-500">{item.razorpayOrderId}</div>
                                        </td>
                                        <td className="p-3 font-mono text-slate-300">
                                            #{item.customer ? item.customer.id : "N/A"}
                                        </td>
                                        <td className="p-3 text-slate-300">
                                            <div>{item.subscription ? item.subscription.description : "One-time Payment"}</div>
                                            {item.subscription && (
                                                <div className="text-[11px] text-slate-500">Sub #{item.subscription.id}</div>
                                            )}
                                        </td>
                                        <td className="p-3 font-semibold text-emerald-400">₹{item.amount?.toFixed(2)}</td>
                                        <td className="p-3 text-xs text-slate-400">
                                            {item.initiatedAt ? new Date(item.initiatedAt).toLocaleString() : "N/A"}
                                        </td>
                                        <td className="p-3">
                                            <select
                                                value={selectedResponses[item.id] || "SUCCESS"}
                                                onChange={(e) => handleResponseChange(item.id, e.target.value)}
                                                className="bg-slate-800 border border-slate-700 rounded-lg p-2 text-xs text-white focus:outline-none focus:border-purple-500 w-full min-w-[200px]"
                                            >
                                                {BANK_RESPONSES.map((resp) => (
                                                    <option key={resp.code} value={resp.code}>{resp.label}</option>
                                                ))}
                                            </select>
                                        </td>
                                        <td className="p-3 text-right">
                                            <button
                                                onClick={() => handleSendResponse(item)}
                                                disabled={isSubmitting}
                                                className="px-3 py-1.5 bg-purple-600 hover:bg-purple-500 active:bg-purple-700 disabled:opacity-50 text-xs font-semibold rounded-lg text-white transition-colors"
                                            >
                                                {isSubmitting ? "Sending..." : "Submit Response"}
                                            </button>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}