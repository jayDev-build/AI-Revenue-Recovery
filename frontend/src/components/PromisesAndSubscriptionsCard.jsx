import React, { useState, useEffect } from 'react';
import { 
    fetchPromisesByCustomer, 
    payPromiseApi, 
    initiatePromisePaymentApi,
    fetchSubscriptionsByCustomer, 
    paySubscriptionApi,
    initiateSubscriptionPaymentApi
} from '../services/api';

export function PromisesAndSubscriptionsCard({ userId, openRazorpayCheckout, handleResolveAmount }) {
    const [promises, setPromises] = useState([]);
    const [subscriptions, setSubscriptions] = useState([]);
    const [loading, setLoading] = useState(false);

    const fetchData = async () => {
        try {
            const [promRes, subRes] = await Promise.all([
                fetchPromisesByCustomer(userId),
                fetchSubscriptionsByCustomer(userId)
            ]);
            setPromises(promRes.data || []);
            setSubscriptions(subRes.data || []);
        } catch (err) {
            console.error("Failed to fetch user data:", err);
        }
    };

    useEffect(() => {
        fetchData();
        const interval = setInterval(fetchData, 5000);
        return () => clearInterval(interval);
    }, [userId]);

    const handlePayPromise = async (promiseId) => {
        setLoading(true);
        try {
            // Get real Razorpay Order ID from backend
            const initRes = await initiatePromisePaymentApi(promiseId);
            const { razorpayOrderId, amount } = initRes.data;

            const mockPaymentData = {
                id: `pay_${Date.now()}`,
                amount: amount,
                razorpayOrderId: razorpayOrderId,
                promiseId: promiseId,
                type: 'PROMISE'
            };
            openRazorpayCheckout(mockPaymentData, async () => {
                await payPromiseApi(promiseId);
                fetchData();
                if (handleResolveAmount) {
                    await handleResolveAmount(userId);
                }
            });
        } catch (err) {
            console.error("Error initiating promise payment:", err);
            alert("Failed to initiate payment. Check Razorpay credentials.");
        } finally {
            setLoading(false);
        }
    };

    const handlePaySubscription = async (subscriptionId) => {
        setLoading(true);
        try {
            // Get real Razorpay Order ID from backend
            const initRes = await initiateSubscriptionPaymentApi(subscriptionId);
            const { razorpayOrderId, amount } = initRes.data;

            const mockPaymentData = {
                id: `pay_${Date.now()}`,
                amount: amount,
                razorpayOrderId: razorpayOrderId,
                subscriptionId: subscriptionId,
                type: 'SUBSCRIPTION'
            };
            openRazorpayCheckout(mockPaymentData, async () => {
                await paySubscriptionApi(subscriptionId);
                fetchData();
                if (handleResolveAmount) {
                    await handleResolveAmount(userId);
                }
            });
        } catch (err) {
            console.error("Error initiating subscription payment:", err);
            alert("Failed to initiate payment. Check Razorpay credentials.");
        } finally {
            setLoading(false);
        }
    };

    const pendingPromises = promises.filter(p => p.status === 'PENDING');
    const brokenCount = promises.filter(p => p.status === 'BROKEN').length;
    const dueSubscriptions = subscriptions.filter(s => s.status === 'PAST_DUE' || s.status === 'CANCELLED'); // Or just PAST_DUE

    if (pendingPromises.length === 0 && dueSubscriptions.length === 0 && brokenCount === 0) {
        return null; // Don't show if nothing to do
    }

    return (
        <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg mb-6">
            <div className="flex justify-between items-center mb-4">
                <h2 className="text-xl font-bold text-white">Action Required</h2>
                {brokenCount > 0 && (
                    <span className="text-xs font-semibold px-2 py-1 bg-red-900/50 text-red-400 rounded-full border border-red-800">
                        {brokenCount} Broken Promise{brokenCount > 1 ? 's' : ''}
                    </span>
                )}
            </div>

            {pendingPromises.length > 0 && (
                <div className="mb-4">
                    <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-2">Promises to Pay</h3>
                    <div className="space-y-3">
                        {pendingPromises.map(p => (
                            <div key={p.id} className="flex justify-between items-center bg-slate-900 p-3 rounded-lg border border-slate-700">
                                <div>
                                    <p className="text-sm text-white">Promise on {p.extractedPromiseDate}</p>
                                    <p className="text-xs text-slate-400">"{p.rawMessage}"</p>
                                </div>
                                <button
                                    onClick={() => handlePayPromise(p.id)}
                                    disabled={loading}
                                    className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold rounded-lg shadow disabled:opacity-50 transition-colors"
                                >
                                    Pay Now
                                </button>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {dueSubscriptions.length > 0 && (
                <div>
                    <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider mb-2">Due Subscriptions</h3>
                    <div className="space-y-3">
                        {dueSubscriptions.map(s => (
                            <div key={s.id} className="flex justify-between items-center bg-slate-900 p-3 rounded-lg border border-slate-700">
                                <div>
                                    <p className="text-sm text-white">{s.description || 'Subscription'}</p>
                                    <p className="text-xs text-slate-400">Amount: ₹{s.planAmount}</p>
                                </div>
                                <button
                                    onClick={() => handlePaySubscription(s.id)}
                                    disabled={loading}
                                    className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold rounded-lg shadow disabled:opacity-50 transition-colors"
                                >
                                    Pay Now
                                </button>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
