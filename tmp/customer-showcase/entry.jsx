import React from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Routes, Route, Navigate } from 'react-router-dom';
import { renderToStaticMarkup } from 'react-dom/server';
import Dashboard from 'pahal/Dashboard.jsx';
import NewBill from 'pahal/NewBill.jsx';
import Inventory from 'pahal/Inventory.jsx';
import Dues from 'pahal/Dues.jsx';
import { PurchaseList } from 'pahal/Purchases.jsx';
import SalesProfitReport from 'pahal/reports/SalesProfitReport.jsx';
import BillingLayout from 'pahal/BillingLayout.jsx';
import { CapabilitiesContext } from 'pahal/licensing/CapabilitiesContext.js';
import { demoData, demoProducts, demoBills, demoPurchases, demoDashboard } from './demo-api.js';

const values = new Map([['tenantId', 'SAMPLE_STORE'], ['userId', 'sample-admin'], ['user', JSON.stringify({ name: 'Sample Administrator', userId: 'sample-admin', role: 'ROLE_ADMIN', tenantId: 'SAMPLE_STORE', is_FirstTimeLogin: false })], ['token', 'sample.' + btoa(JSON.stringify({ sub: 'sample-admin', role: 'ROLE_ADMIN', tenantId: 'SAMPLE_STORE' })) + '.sample']]);
window.__pahalDemoStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: key => values.delete(key), clear: () => values.clear() };
window.__PAHAL_DEMO_DATA__ = { products: demoProducts, bills: demoBills, purchases: demoPurchases, dashboard: demoDashboard, get: demoData };
window.__PAHAL_DEMO_BILL__ = { customerName: 'Sample Customer A', contactNumber: '9000000001', selectedSalesman: 'SAMPLE-01', salesmanQuery: 'Sample Salesperson', cart: demoProducts.slice(0, 4).map((p, i) => ({ ...p, cartLineId: `${p.id}:${p.barcode}`, qty: [2, 4, 1, 2][i], quantityPerScan: 1, discount: 0, discountType: 'PERCENT' })) };

const routes = ['/dashboard', '/bill/new', '/inventory', '/bill/dues', '/purchases', '/reports'];
function screen() {
  const index = Math.max(0, Math.min(5, Number(window.__PAHAL_SCREEN__ || 0)));
  return <MemoryRouter initialEntries={[routes[index]]}><Routes><Route element={<BillingLayout />}><Route path='/dashboard' element={<Dashboard/>}/><Route path='/bill/new' element={<NewBill/>}/><Route path='/inventory' element={<Inventory/>}/><Route path='/bill/dues' element={<Dues/>}/><Route path='/customers' element={<Dues/>}/><Route path='/purchases' element={<PurchaseList/>}/><Route path='/reports' element={<SalesProfitReport/>}/><Route path='/masters/products' element={<Inventory/>}/><Route path='*' element={<Navigate to='/dashboard' replace/>}/></Route></Routes></MemoryRouter>;
}
class DemoBoundary extends React.Component {
  constructor(props) { super(props); this.state = { error: '' }; }
  static getDerivedStateFromError(error) { return { error: error.message }; }
  render() { return this.state.error ? <div style={{ padding: 32, fontFamily: 'Segoe UI, sans-serif' }}><h2>Open a guided demonstration</h2><p>This sample screen could not be displayed. The product overview remains available.</p><p>{this.state.error}</p></div> : this.props.children; }
}
window.__PAHAL_RENDER_STATIC__ = index => {
  window.__PAHAL_SCREEN__ = index;
  const Component = [Dashboard, NewBill, Inventory, Dues, PurchaseList, SalesProfitReport][index];
  const capability = { license: demoData('/me/capabilities').license, loading: false, error: '', can: key => demoData('/me/capabilities').allowedActions[key] === true, refresh: async () => demoData('/me/capabilities') };
  return renderToStaticMarkup(<CapabilitiesContext.Provider value={capability}><MemoryRouter initialEntries={[routes[index]]}><Component/></MemoryRouter></CapabilitiesContext.Provider>);
};
if (!window.__PAHAL_STATIC_ONLY__) createRoot(document.getElementById('demo-root')).render(<DemoBoundary>{screen()}</DemoBoundary>);
