import React, { useState, useEffect } from 'react';
import axios from 'axios';

const API_BASE = 'http://localhost:8080';

function App() {
  const [bankHealth, setBankHealth] = useState([]);
  const [paymentStatus, setPaymentStatus] = useState(null);
  const [userId, setUserId] = useState(1);
  const [loading, setLoading] = useState(false);
  const [amount, setAmount] = useState('500');

  const fetchBankHealth = async () => {
    try {
      const res = await axios.get(`${API_BASE}/bank-health`);
      setBankHealth(res.data);
    } catch (err) {
      console.error('Failed to fetch bank health:', err);
    }
  };

  useEffect(() => {
    fetchBankHealth();
  }, []);

  const handleSeed = async () => {
    setLoading(true);
    try {
      await axios.post(`${API_BASE}/seed`);
      alert('Demo data seeded successfully!');
      fetchBankHealth();
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleInitiatePayment = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const payload = {
        customerId: userId,
        amount: amount,
        method: 'UPI'
      };
      const res = await axios.post(`${API_BASE}/payments/initiate`, payload);
      setPaymentStatus(res.data);

      if (res.data.razorpayOrderId) {
        openRazorpayCheckout(res.data);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const openRazorpayCheckout = (paymentData) => {
    if (!window.Razorpay) {
      alert("Razorpay SDK failed to load. Ensure checkout.js is in index.html");
      return;
    }

    const options = {
      key: "rzp_test_TTaXtsmQ29Iwo0",
      // Convert amount to paise (e.g. 500 INR = 50000 Paise)
      amount: Math.round(Number(paymentData.amount) * 100),
      currency: "INR",
      name: "AI Revenue Recovery",
      description: "Test Transaction",
      order_id: paymentData.razorpayOrderId,
      handler: function (response) {
        alert("Payment Successful! Payment ID: " + response.razorpay_payment_id);
        checkStatus(paymentData.id);
      },
      prefill: {
        name: "Yashit",
        email: "yashit@example.com",
        contact: "9999999999"
      },
      theme: {
        color: "#3399cc"
      }
    };

    const rzp1 = new window.Razorpay(options);
    rzp1.on('payment.failed', function (response) {
      alert("Payment Failed: " + response.error.description);
      checkStatus(paymentData.id);
    });
    rzp1.open();
    // Removed broken async axios.get block
  };

  const handleResolve = async (id) => {
    setLoading(true);
    try {
      const res = await axios.post(`${API_BASE}/payments/${id}/resolve`);
      setPaymentStatus(res.data);
      alert(`Resolved Status: ${res.data.status}`);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const checkStatus = async (id) => {
    try {
      const res = await axios.get(`${API_BASE}/payments/${id}/status`);
      setPaymentStatus(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="min-h-screen p-8 bg-slate-900 text-slate-100 font-sans">
      <div className="max-w-5xl mx-auto space-y-12">

        <header className="flex justify-between items-center pb-6 border-b border-slate-700">
          <div>
            <h1 className="text-4xl font-extrabold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent">
              AI Revenue Recovery
            </h1>
            <p className="text-slate-400 mt-2">Self-Healing Payment Degradation Demo</p>
          </div>
          <button
            onClick={handleSeed}
            disabled={loading}
            className="px-6 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg font-medium transition-colors border border-slate-600"
          >
            Seed Demo Data
          </button>
        </header>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Payment Section */}
          <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl">
            <h2 className="text-2xl font-bold mb-6 flex items-center">
              <svg className="w-6 h-6 mr-3 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" /></svg>
              Initiate Checkout
            </h2>
            <form onSubmit={handleInitiatePayment} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-400 mb-1">USER-ID </label>
                <input
                  type="number"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-400 mb-1">Amount (INR)</label>
                <input
                  type="number"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  required
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-lg font-bold transition-all shadow-lg shadow-blue-900/50"
              >
                {loading ? 'Processing...' : 'Pay with Razorpay'}
              </button>
            </form>

            {paymentStatus && (
              <div className="mt-8 p-4 bg-slate-900 rounded-xl border border-slate-700">
                <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-3">Latest Payment Attempt</h3>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-slate-500">ID:</span>
                    <span className="font-mono text-slate-300">{paymentStatus.id}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Order ID:</span>
                    <span className="font-mono text-blue-400">{paymentStatus.razorpayOrderId || 'N/A'}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500">Status:</span>
                    <span className={`px-2 py-1 rounded text-xs font-bold ${paymentStatus.status === 'AMBIGUOUS' ? 'bg-orange-500/20 text-orange-400' :
                      paymentStatus.status === 'CAPTURED' ? 'bg-green-500/20 text-green-400' :
                        'bg-slate-700 text-slate-300'
                      }`}>
                      {paymentStatus.status}
                    </span>
                  </div>
                </div>

                {paymentStatus.status === 'AMBIGUOUS' && (
                  <div className="mt-4 pt-4 border-t border-slate-700">
                    <div className="flex items-start mb-4">
                      <svg className="w-5 h-5 text-orange-400 mr-2 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg>
                      <p className="text-xs text-orange-300">
                        Payment is stuck in an AMBIGUOUS state. In a real scenario, the AI background job would resolve this automatically.
                      </p>
                    </div>
                    <button
                      onClick={() => handleResolve(paymentStatus.id)}
                      disabled={loading}
                      className="w-full py-2 bg-orange-500 hover:bg-orange-400 text-white rounded-lg font-bold transition-colors text-sm shadow-lg shadow-orange-900/20"
                    >
                      Run AI Resolve (Self-Heal)
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Bank Health Section */}
          <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-2xl font-bold flex items-center">
                <svg className="w-6 h-6 mr-3 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
                Bank Health Grid
              </h2>
              <button onClick={fetchBankHealth} className="text-slate-400 hover:text-white transition-colors">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" /></svg>
              </button>
            </div>

            <div className="space-y-4">
              {bankHealth.length === 0 ? (
                <p className="text-slate-500 text-center py-8">No bank health data available. Seed data first.</p>
              ) : (
                bankHealth.map(bank => (
                  <div key={bank.id} className="p-4 rounded-xl bg-slate-900/50 border border-slate-700 flex justify-between items-center">
                    <div>
                      <h4 className="font-bold text-slate-200">{bank.bankName}</h4>
                      <p className="text-xs text-slate-500">Success Rate: <span className="text-slate-300 font-mono">{bank.successRate}%</span></p>
                    </div>
                    <span className={`px-3 py-1 rounded-full text-xs font-bold ${bank.status === 'UP' ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'
                      }`}>
                      {bank.status}
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}

export default App;