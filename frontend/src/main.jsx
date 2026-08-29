import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';

import './index.css'
import App from './App.jsx'
import SubscriptionManagementPage from './components/SubscriptionManagementPage.jsx';
import PendingBankRequestsPage from './components/PendingBankRequestsPage.jsx';

createRoot(document.getElementById('root')).render(
  // <StrictMode>
  <Router>
    <Routes>
      <Route path="/" element={<App />} />
      <Route path="/subscription" element={<SubscriptionManagementPage />} />
      <Route path='/bank/pending' element={<PendingBankRequestsPage />} />
    </Routes>
  </Router>
  // </StrictMode>, 
)
