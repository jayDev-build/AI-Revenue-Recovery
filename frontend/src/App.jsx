import React, { useState, useEffect, use } from 'react';
import { Header } from './components/Header';
import { CheckoutForm } from './components/CheckoutForm';
import { PaymentStatusCard } from './components/PaymentStatusCard';
import { BankHealthGrid } from './components/BankHealthGrid';
import { AuditLogFeed } from './components/AuditLogFeed';
import RecoveredRevenueCard from './components/RecoveredRevenueCard';
import { PromisesAndSubscriptionsCard } from './components/PromisesAndSubscriptionsCard';
import {
  fetchBankHealthApi,
  fetchAuditLogsApi,
  fetchLatestPaymentApi,
  seedFailuresApi,
  initiatePaymentApi,
  resolvePaymentApi,
  checkPaymentStatusApi,
  getRecoveredAmount
} from './services/api';

const BANK_OPTIONS = ['HDFC UPI', 'ICICI NetBanking', 'SBI UPI', 'Bank X'];

function App() {
  const [bankHealth, setBankHealth] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [paymentStatus, setPaymentStatus] = useState(null);
  const [highlightedBanks, setHighlightedBanks] = useState({});

  const [userId, setUserId] = useState(1);
  const [amount, setAmount] = useState('500');
  const [checkoutBank, setCheckoutBank] = useState('HDFC UPI');
  const [simulateDrop, setSimulateDrop] = useState(false);

  const [seedBank, setSeedBank] = useState('HDFC UPI');
  const [loading, setLoading] = useState(false);

  const [recovered, setRecovered] = useState(0);

  const fetchBankHealth = async () => {
    try {
      const res = await fetchBankHealthApi();
      setBankHealth((current) => {
        const newMap = {};
        res.data.forEach((b) => (newMap[b.bankName] = b));

        const newlyHighlighted = {};
        current.forEach((oldB) => {
          const newB = newMap[oldB.bankName];
          if (newB && (newB.successRate !== oldB.successRate || newB.isDegraded !== oldB.isDegraded)) {
            newlyHighlighted[oldB.bankName] = true;
          }
        });

        if (Object.keys(newlyHighlighted).length > 0) {
          setHighlightedBanks((prev) => ({ ...prev, ...newlyHighlighted }));
          setTimeout(() => {
            setHighlightedBanks((prev) => {
              const updated = { ...prev };
              Object.keys(newlyHighlighted).forEach((k) => delete updated[k]);
              return updated;
            });
          }, 1500);
        }

        return res.data;
      });
    } catch (err) {
      console.error('Failed to fetch bank health:', err);
    }
  };

  const fetchAuditLogs = async () => {
    try {
      const res = await fetchAuditLogsApi();
      setAuditLogs(res.data);
    } catch (err) {
      console.error('Failed to fetch audit logs:', err);
    }
  };

  const fetchLatestPayment = async () => {
    try {
      const res = await fetchLatestPaymentApi();
      if (res.data) setPaymentStatus(res.data);
    } catch (err) {
      console.error('Failed to fetch latest payment:', err);
    }
  };

  useEffect(() => {
    const syncData = async () => {
      await Promise.all([fetchBankHealth(), fetchAuditLogs(), fetchLatestPayment()]);
      try {
        const res = await getRecoveredAmount(userId);
        setRecovered(res.data);
      } catch (err) {
        console.error(err);
      }
    };

    syncData();
    const interval = setInterval(syncData, 3000);
    return () => clearInterval(interval);
  }, [userId]);

  const handleSeedFailures = async () => {
    setLoading(true);
    try {
      await seedFailuresApi(seedBank, 30);
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
      const payload = { customerId: userId, amount, method: 'UPI', bankName: checkoutBank, simulateDrop };
      const res = await initiatePaymentApi(payload);
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

  const openRazorpayCheckout = (paymentData, onSuccess) => {
    if (!window.Razorpay) {
      alert('Razorpay SDK failed to load. Ensure checkout.js is in index.html');
      return;
    }

    const options = {
      key: 'rzp_test_TTaXtsmQ29Iwo0',
      amount: Math.round(Number(paymentData.amount) * 100),
      currency: 'INR',
      name: 'AI Revenue Recovery',
      description: 'Test Transaction',
      order_id: paymentData.razorpayOrderId,
      handler: () => {
        if (onSuccess) {
            onSuccess();
        } else {
            checkStatus(paymentData.id);
        }
      },
      prefill: { name: 'Demo User', email: 'demo@example.com', contact: '9999999999' },
      theme: { color: '#3399cc' }
    };

    const rzp1 = new window.Razorpay(options);
    rzp1.on('payment.failed', () => {
        if (!onSuccess) checkStatus(paymentData.id);
    });
    rzp1.on('payment.success', () => {
        if (!onSuccess) checkStatus(paymentData.id);
    });
    rzp1.open();
  };

  const handleResolve = async (id) => {
    setLoading(true);
    try {
      const res = await resolvePaymentApi(id);
      setPaymentStatus(res.data);
      await fetchAuditLogs();
      await handleResolveAmount(userId);

    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }

  };

  const handleResolveAmount = async (id) => {
    setLoading(true);
    try {
      const res = await getRecoveredAmount(id);
      console.log(res);
      setRecovered(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const checkStatus = async (id) => {
    try {
      const res = await checkPaymentStatusApi(id);
      setPaymentStatus(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const selectedBankSnapshot = bankHealth.find((b) => b.bankName === checkoutBank);
  const isSelectedBankDegraded = selectedBankSnapshot && selectedBankSnapshot.successRate < 0.70;

  return (
    <div className="min-h-screen p-8 bg-slate-900 text-slate-100 font-sans">
      <div className="max-w-6xl mx-auto space-y-8">
        <Header
          seedBank={seedBank}
          setSeedBank={setSeedBank}
          bankOptions={BANK_OPTIONS}
          onSeedFailures={handleSeedFailures}
          loading={loading}
        />

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="space-y-6">
            <PromisesAndSubscriptionsCard userId={userId} openRazorpayCheckout={openRazorpayCheckout} handleResolveAmount={handleResolveAmount} />
            <CheckoutForm
              userId={userId}
              setUserId={setUserId}
              amount={amount}
              setAmount={setAmount}
              checkoutBank={checkoutBank}
              setCheckoutBank={setCheckoutBank}
              simulateDrop={simulateDrop}
              setSimulateDrop={setSimulateDrop}
              bankOptions={BANK_OPTIONS}
              isSelectedBankDegraded={isSelectedBankDegraded}
              selectedBankSnapshot={selectedBankSnapshot}
              onSubmit={handleInitiatePayment}
              loading={loading}
            />
            <PaymentStatusCard
              paymentStatus={paymentStatus}
              onCheckStatus={checkStatus}
              onResolve={handleResolve}
              loading={loading}
            />
          </div>

          <div className="space-y-6">
            <RecoveredRevenueCard amount={recovered} userId={userId} handleResolveAmount={handleResolveAmount} />
            <BankHealthGrid bankHealth={bankHealth} highlightedBanks={highlightedBanks} />
            <AuditLogFeed auditLogs={auditLogs} />
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;