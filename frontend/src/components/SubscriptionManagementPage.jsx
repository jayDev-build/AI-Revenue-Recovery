import React, { useState, useEffect } from "react";
import SubscriptionForm from "./SubscriptionForm";
import SubscriptionList from "./SubscriptionList";
import SubscriptionTransactions from "./SubscriptionTransactions";
import { getAllSubscriptionList } from "../services/api";

export default function SubscriptionManagementPage() {
    const [subscriptions, setSubscriptions] = useState([]);
    const [transactions, setTransactions] = useState([]);
    const [loading, setLoading] = useState(true);

    // 1. Fetch Subscriptions and Transactions on initial mount
    const fetchData = async () => {
        try {
            const [subRes, txnRes] = await Promise.all([
                getAllSubscriptionList(),
                // fetch("/api/payments/transactions")
            ]);

            // console.log(subRes)

            if (subRes.status === 200) {
                const subData = await subRes.data;
                setSubscriptions(subData);
                // console.log(subData);
            }

            // if (txnRes.ok) {
            //     const txnData = await txnRes.json();
            //     setTransactions(txnData);
            // }
        } catch (err) {
            console.error("Error loading subscription dashboard data:", err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    // 2. Callback when a new subscription is created from SubscriptionForm
    const handleSubscriptionCreated = (newSub) => {
        setSubscriptions((prev) => [newSub, ...prev]);
    };

    // 3. Callback when status is toggled in SubscriptionList
    const handleStatusChange = (id, newStatus) => {
        setSubscriptions((prev) =>
            prev.map((sub) => (sub.id === id ? { ...sub, status: newStatus } : sub))
        );
    };

    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 p-6 md:p-10 space-y-8">
            {/* Header Banner */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-6">
                <div>
                    <h1 className="text-2xl font-bold text-white tracking-tight">
                        Subscription Control Center
                    </h1>
                    <p className="text-sm text-slate-400 mt-1">
                        Manage recurring plans, status toggles, and successful recovery transactions.
                    </p>
                </div>
                <button
                    onClick={fetchData}
                    className="self-start md:self-auto px-4 py-2 bg-slate-900 border border-slate-700 hover:bg-slate-800 rounded-xl text-xs font-semibold text-slate-300 transition-colors flex items-center gap-2"
                >
                    <span>Refresh Dashboard Data</span>
                </button>
            </div>

            {loading ? (
                <div className="p-12 text-center text-slate-400 font-medium">
                    Loading dashboard metrics...
                </div>
            ) : (
                /* Responsive Layout Grid */
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
                    {/* Left Column: Creation Form (4 cols on desktop) */}
                    <div className="lg:col-span-4 flex justify-center">
                        <SubscriptionForm onSubmitSuccess={handleSubscriptionCreated} />
                    </div>

                    {/* Right Column: Active Lists & Transactions (8 cols on desktop) */}
                    <div className="lg:col-span-8 space-y-8">
                        {/* Active / Paused Subscriptions */}
                        <SubscriptionList
                            subscriptions={subscriptions}
                            onStatusChange={handleStatusChange}
                        />

                        {/* Successful Transactions History */}
                        <SubscriptionTransactions transactions={transactions} />
                    </div>
                </div>
            )}
        </div>
    );
}