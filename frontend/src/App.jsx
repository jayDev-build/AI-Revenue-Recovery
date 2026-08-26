import React, { useState, useEffect } from 'react';
import axios from 'axios';

const API_BASE = 'http://localhost:8080';

const BANK_OPTIONS = ['HDFC UPI', 'ICICI NetBanking', 'SBI UPI', 'Bank X'];

function App() {
  const [bankHealth, setBankHealth] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [paymentStatus, setPaymentStatus] = useState(null);
  
  const [userId, setUserId] = useState(1);
  const [amount, setAmount] = useState('500');
  const [checkoutBank, setCheckoutBank] = useState('HDFC UPI');
  const [simulateDrop, setSimulateDrop] = useState(false);
  
  const [seedBank, setSeedBank] = useState('HDFC UPI');
  const [loading, setLoading] = useState(false);

  const fetchBankHealth = async () => {
    try {
      const res = await axios.get(`${API_BASE}/bank-health`);
      // Filter to keep only the latest snapshot per bank
      const latestSnapshots = {};
      res.data.forEach(snapshot => {
        if (!latestSnapshots[snapshot.bankName] || snapshot.id > latestSnapshots[snapshot.bankName].id) {
          latestSnapshots[snapshot.bankName] = snapshot;
        }
      });
      setBankHealth(Object.values(latestSnapshots));
    } catch (err) {
      console.error('Failed to fetch bank health:', err);
    }
  };

  const fetchAuditLogs = async () => {
    try {
      const res = await axios.get(`${API_BASE}/audit-logs/recent`);
      setAuditLogs(res.data);
    } catch (err) {
      console.error('Failed to fetch audit logs:', err);
    }
  };

  useEffect(() => {
    fetchBankHealth();
    fetchAuditLogs();
    
    // Poll for updates every 5 seconds
    const interval = setInterval(() => {
      fetchBankHealth();
      fetchAuditLogs();
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  const handleSeedFailures = async () => {
    setLoading(true);
    try {
      await axios.post(`${API_BASE}/demo/seed-failures`, {
        bankName: seedBank,
        failureCount: 30
      });
      alert(`Simulated 30 failures for ${seedBank}`);
      fetchBankHealth();
      fetchAuditLogs();
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
        method: 'UPI',
        bankName: checkoutBank,
        simulateDrop: simulateDrop
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
      key: "rzp_test_TTaXtsmQ29Iwo0", // Ensure this matches backend or is a generic test key
      amount: Math.round(Number(paymentData.amount) * 100),
      currency: "INR",
      name: "AI Revenue Recovery",
      description: "Test Transaction",
      order_id: paymentData.razorpayOrderId,
      handler: function (response) {
        checkStatus(paymentData.id);
      },
      prefill: {
        name: "Demo User",
        email: "demo@example.com",
        contact: "9999999999"
      },
      theme: { color: "#3399cc" }
    };

    const rzp1 = new window.Razorpay(options);
    rzp1.on('payment.failed', function (response) {
      checkStatus(paymentData.id);
    });
    rzp1.open();
  };

  const handleResolve = async (id) => {
    setLoading(true);
    try {
      const res = await axios.post(`${API_BASE}/payments/${id}/resolve`);
      setPaymentStatus(res.data);
      fetchAuditLogs();
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

  const selectedBankSnapshot = bankHealth.find(b => b.bankName === checkoutBank);
  const isSelectedBankDegraded = selectedBankSnapshot && selectedBankSnapshot.successRate < 0.70;

  return (
    <div className="min-h-screen p-8 bg-slate-900 text-slate-100 font-sans">
      <div className="max-w-6xl mx-auto space-y-8">

        {/* Header & Demo Control Bar */}
        <header className="flex flex-col md:flex-row justify-between items-center pb-6 border-b border-slate-700 gap-4">
          <div>
            <h1 className="text-4xl font-extrabold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent">
              AI Revenue Recovery
            </h1>
            <p className="text-slate-400 mt-2">Flow 1: Payment Degradation & Lost Acknowledgment</p>
          </div>
          
          <div className="flex items-center space-x-3 bg-slate-800 p-3 rounded-xl border border-slate-700">
            <span className="text-sm font-bold text-slate-400">DEMO ACTIONS:</span>
            <select 
              value={seedBank} 
              onChange={(e) => setSeedBank(e.target.value)}
              className="bg-slate-900 border border-slate-600 rounded-lg px-3 py-2 text-sm text-slate-200 focus:outline-none"
            >
              {BANK_OPTIONS.map(b => <option key={b} value={b}>{b}</option>)}
            </select>
            <button
              onClick={handleSeedFailures}
              disabled={loading}
              className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white rounded-lg text-sm font-bold shadow-lg shadow-red-900/50 transition-all"
            >
              Simulate 30 Failures
            </button>
          </div>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Left Panel: Checkout */}
          <div className="space-y-6">
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden">
              <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-blue-500 to-purple-500"></div>
              <h2 className="text-2xl font-bold mb-6 flex items-center">
                <svg className="w-6 h-6 mr-3 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" /></svg>
                Checkout Terminal
              </h2>

              {isSelectedBankDegraded && (
                <div className="mb-6 p-4 bg-orange-500/10 border border-orange-500/50 rounded-lg flex items-start">
                  <svg className="w-6 h-6 text-orange-400 mr-3 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg>
                  <div>
                    <h4 className="text-orange-400 font-bold text-sm">⚠️ Degraded Success Rate</h4>
                    <p className="text-orange-300 text-xs mt-1">
                      {checkoutBank} is currently experiencing a low success rate ({(selectedBankSnapshot.successRate * 100).toFixed(0)}%). Consider using an alternate method.
                    </p>
                  </div>
                </div>
              )}

              <form onSubmit={handleInitiatePayment} className="space-y-5">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-slate-400 mb-1">User ID</label>
                    <input type="number" value={userId} onChange={(e) => setUserId(e.target.value)} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100" />
                  </div>
                  <div> 
                    <label className="block text-sm font-medium text-slate-400 mb-1">Amount (INR)</label>
                    <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100" />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium text-slate-400 mb-1">Select Bank</label>
                  <select value={checkoutBank} onChange={(e) => setCheckoutBank(e.target.value)} className="w-full bg-slate-900 border border-slate-700 rounded-lg px-4 py-2 text-slate-100 focus:outline-none focus:border-blue-500">
                    {BANK_OPTIONS.map(b => <option key={b} value={b}>{b}</option>)}
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

                <button type="submit" disabled={loading} className="w-full py-3 bg-blue-600 hover:bg-blue-500 text-white rounded-lg font-bold transition-all shadow-lg shadow-blue-900/50">
                  {loading ? 'Processing...' : 'Pay Now'}
                </button>
              </form>
            </div>

            {/* Payment Status Box */}
            {paymentStatus && (
              <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden transition-all duration-500">
                <div className={`absolute top-0 left-0 w-1 h-full ${
                  paymentStatus.status === 'CAPTURED' ? 'bg-green-500' :
                  paymentStatus.status === 'INITIATED' ? 'bg-yellow-500' :
                  paymentStatus.status === 'FAILED' ? 'bg-red-500' : 'bg-slate-500'
                }`}></div>
                <div className="pl-4">
                  <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-4 flex justify-between items-center">
                    Latest Attempt Status
                    <button onClick={() => checkStatus(paymentStatus.id)} className="text-blue-400 hover:text-blue-300">
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" /></svg>
                    </button>
                  </h3>
                  
                  <div className="grid grid-cols-2 gap-y-3 text-sm mb-5">
                    <span className="text-slate-500">Attempt ID:</span>
                    <span className="font-mono text-slate-300 text-right">{paymentStatus.id}</span>
                    <span className="text-slate-500">Order ID:</span>
                    <span className="font-mono text-blue-400 text-right truncate" title={paymentStatus.razorpayOrderId}>{paymentStatus.razorpayOrderId || 'N/A'}</span>
                    <span className="text-slate-500 font-medium">Status:</span>
                    <div className="text-right">
                      <span className={`px-2.5 py-1 rounded-md text-xs font-bold inline-block ${
                        paymentStatus.status === 'INITIATED' ? 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/30' :
                        paymentStatus.status === 'CAPTURED' ? 'bg-green-500/20 text-green-400 border border-green-500/30' :
                        paymentStatus.status === 'FAILED' ? 'bg-red-500/20 text-red-400 border border-red-500/30' :
                        'bg-slate-700 text-slate-300'
                      }`}>
                        {paymentStatus.status}
                      </span>
                    </div>
                  </div>

                  {paymentStatus.status === 'INITIATED' && (
                    <div className="pt-4 border-t border-slate-700 animate-pulse">
                      <div className="flex items-start mb-4">
                        <svg className="w-5 h-5 text-yellow-500 mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                        <p className="text-xs text-yellow-400/80">
                          Payment stuck in INITIATED due to lost webhook acknowledgment.
                        </p>
                      </div>
                      <button
                        onClick={() => handleResolve(paymentStatus.id)}
                        disabled={loading}
                        className="w-full py-2.5 bg-gradient-to-r from-yellow-600 to-orange-600 hover:from-yellow-500 hover:to-orange-500 text-white rounded-lg font-bold transition-all text-sm shadow-lg shadow-orange-900/30 flex items-center justify-center"
                      >
                        <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>
                        ⚡ Resolve Status (Self-Heal)
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Right Panel: Health & Logs */}
          <div className="space-y-6">
            
            {/* Bank Health Grid */}
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden">
              <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-purple-500 to-pink-500"></div>
              <h2 className="text-2xl font-bold mb-6 flex items-center">
                <svg className="w-6 h-6 mr-3 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
                Bank Health Grid
              </h2>

              <div className="space-y-4">
                {bankHealth.length === 0 ? (
                  <p className="text-slate-500 text-center py-8 text-sm">No recent data. Initiate a payment or seed failures.</p>
                ) : (
                  bankHealth.map(bank => {
                    const isDegraded = bank.successRate < 0.70;
                    const pct = (bank.successRate * 100).toFixed(0);
                    return (
                      <div key={bank.id} className={`p-4 rounded-xl border ${isDegraded ? 'bg-red-900/10 border-red-500/30' : 'bg-slate-900/50 border-slate-700'} transition-all`}>
                        <div className="flex justify-between items-center mb-2">
                          <h4 className="font-bold text-slate-200">{bank.bankName}</h4>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider ${isDegraded ? 'bg-red-500/20 text-red-400' : 'bg-green-500/20 text-green-400'}`}>
                            {isDegraded ? 'CRITICAL DIP' : 'NORMAL'}
                          </span>
                        </div>
                        
                        <div className="mb-1 flex justify-between text-xs">
                          <span className="text-slate-500">Success Rate</span>
                          <span className={isDegraded ? 'text-red-400 font-mono font-bold' : 'text-green-400 font-mono font-bold'}>{pct}%</span>
                        </div>
                        <div className="w-full bg-slate-800 rounded-full h-1.5 mb-3 overflow-hidden">
                          <div className={`h-1.5 rounded-full ${isDegraded ? 'bg-red-500' : 'bg-green-500'}`} style={{ width: `${pct}%` }}></div>
                        </div>

                        {isDegraded && bank.aiSummary && (
                          <div className="mt-3 p-3 bg-slate-900 rounded-lg border border-purple-500/20 flex items-start">
                            <div className="w-5 h-5 rounded-full bg-purple-500/20 flex items-center justify-center mr-2 flex-shrink-0 mt-0.5">
                              <span className="text-xs">✨</span>
                            </div>
                            <p className="text-xs text-purple-200/80 leading-relaxed italic">
                              "{bank.aiSummary}"
                            </p>
                          </div>
                        )}
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            {/* Audit Log Feed */}
            <div className="bg-slate-800 rounded-2xl p-6 border border-slate-700 shadow-xl relative overflow-hidden">
              <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-emerald-500 to-teal-500"></div>
              <h2 className="text-2xl font-bold mb-6 flex items-center">
                <svg className="w-6 h-6 mr-3 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                Real-Time Audit Log
              </h2>
              
              <div className="space-y-3 max-h-[400px] overflow-y-auto pr-2 custom-scrollbar">
                {auditLogs.length === 0 ? (
                  <p className="text-slate-500 text-center py-8 text-sm">No audit logs available.</p>
                ) : (
                  auditLogs.map(log => (
                    <div key={log.id} className="p-3 bg-slate-900 rounded-lg border border-slate-700 text-sm">
                      <div className="flex justify-between items-start mb-2">
                        <span className="text-xs font-mono text-emerald-400 bg-emerald-400/10 px-2 py-0.5 rounded">
                          {log.outcome}
                        </span>
                        <span className="text-[10px] text-slate-500">
                          {new Date(log.createdAt).toLocaleTimeString()}
                        </span>
                      </div>
                      <p className="text-slate-300 text-xs mb-1"><span className="text-slate-500">Action:</span> {log.actionTaken}</p>
                      <p className="text-slate-400 text-xs italic">"{log.reasoning}"</p>
                    </div>
                  ))
                )}
              </div>
            </div>

          </div>
        </div>

      </div>
    </div>
  );
}

export default App;