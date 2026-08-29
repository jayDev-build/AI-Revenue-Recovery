import axios from 'axios';

const API_BASE = 'http://localhost:8080';

export const fetchBankHealthApi = () => axios.get(`${API_BASE}/api/bank-health/latest`);
export const fetchAuditLogsApi = () => axios.get(`${API_BASE}/api/audit-logs/recent`);
export const fetchLatestPaymentApi = () => axios.get(`${API_BASE}/api/payments/latest`);
export const seedFailuresApi = (bankName, failureCount = 30) =>
    axios.post(`${API_BASE}/demo/seed-failures`, { bankName, failureCount });
export const initiatePaymentApi = (payload) => axios.post(`${API_BASE}/payments/initiate`, payload);
export const resolvePaymentApi = (id) => axios.post(`${API_BASE}/payments/${id}/resolve`);
export const checkPaymentStatusApi = (id) => axios.get(`${API_BASE}/payments/${id}/status`);
export const getRecoveredAmount = (id) => axios.get(`${API_BASE}/api/recovered-amount/${id}`);
export const createSubscriptionApi = (payload) => axios.post(`${API_BASE}/api/create-subscription`, payload);
export const getAllSubscriptionList = () => axios.get(`${API_BASE}/api/subscriptions`);
export const getAllPendingBankRequests = () => axios.get(`${API_BASE}/api/bank/pending-requests`)