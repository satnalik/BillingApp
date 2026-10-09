const products = [
  { id: 1, name: 'A5 Notebook', barcode: '8901000000011', category: 'Stationery', price: 65, costPrice: 45, stockQuantity: 48 },
  { id: 2, name: 'Ball Pen - Blue', barcode: '8901000000028', category: 'Stationery', price: 15, costPrice: 9, stockQuantity: 120 },
  { id: 3, name: 'Pencil Set', barcode: '8901000000035', category: 'Stationery', price: 40, costPrice: 27, stockQuantity: 8 },
  { id: 4, name: 'Document Folder', barcode: '8901000000042', category: 'Office', price: 35, costPrice: 23, stockQuantity: 64 },
  { id: 5, name: 'Colour Pencils', barcode: '8901000000059', category: 'Art supplies', price: 120, costPrice: 82, stockQuantity: 6 },
  { id: 6, name: 'Glue Stick', barcode: '8901000000066', category: 'Art supplies', price: 30, costPrice: 19, stockQuantity: 32 },
  { id: 7, name: 'Sketch Book', barcode: '8901000000073', category: 'Art supplies', price: 85, costPrice: 59, stockQuantity: 24 },
  { id: 8, name: 'Desk Organiser', barcode: '8901000000080', category: 'Office', price: 250, costPrice: 180, stockQuantity: 12 },
].map(p => ({ ...p, sellingPrice: p.price, mrp: p.price, landingPrice: p.costPrice, supplierName: 'Sample Stationery Supplier', supplierId: 1, itemType: 'PACKAGE', gstRate: 0, hsnCode: '4820', unitCode: 'PCS', taxCategory: 'EXEMPT' }));
const customers = ['Sample Customer A', 'Sample Customer B', 'Sample Customer C'];
const bills = customers.map((name, i) => ({ id: 101 + i, invoiceNumber: `INV-0000010${i + 1}`, customerName: name, contactInfo: `900000000${i + 1}`, createdAt: `2026-10-09T${10 + i}:15:00`, updatedAt: `2026-10-09T${10 + i}:15:00`, totalAmount: [650, 480, 320][i], paidAmount: [400, 300, 200][i], dueAmount: [250, 180, 120][i], salesmanName: 'Sample Salesperson', status: 'ACTIVE', payments: [], items: [] }));
const purchases = [{ id: 11, billNumber: 'SUP-0101', supplierId: 1, supplierName: 'Sample Stationery Supplier', supplierCode: 'SUP-001', billDate: '2026-10-08', totalAmount: 3200, paidAmount: 2000, dueAmount: 1200, status: 'ACTIVE', itemCount: 4 }, { id: 12, billNumber: 'SUP-0102', supplierId: 2, supplierName: 'Sample Office Supplier', supplierCode: 'SUP-002', billDate: '2026-10-09', totalAmount: 1800, paidAmount: 1000, dueAmount: 800, status: 'ACTIVE', itemCount: 3 }];
function statistics(netSales, cost, qty, lines = 6) {
  return { netSales, coveredSales: netSales, costOfGoodsSold: cost, estimatedGrossProfit: +(netSales - cost).toFixed(2), marginPercent: netSales ? +((netSales - cost) / netSales * 100).toFixed(2) : null, billDiscount: 0, gstAmount: 0, quantitySold: qty, costedQuantity: qty, missingCostQuantity: 0, saleLines: lines, costedLines: lines, missingCostLines: 0, lossMakingLines: 0, billsCount: lines, costCoveragePercent: lines ? 100 : 0, coverageStatus: lines ? 'COMPLETE' : 'EMPTY' };
}
const profitItems = products.slice(0, 6).map((p, i) => { const qty = [30, 60, 20, 20, 10, 35][i]; const stats = statistics(p.price * qty, p.costPrice * qty, qty); return { key: String(p.id), productId: p.id, productName: p.name, barcode: p.barcode, category: p.category, statistics: stats, completeGrossProfit: stats.estimatedGrossProfit, completeMarginPercent: stats.marginPercent }; });
const profitSummary = statistics(6600, 4375, 175, 36);
const features = ['BILLING','PRODUCTS','CUSTOMER_DUES','SALES_RETURNS','BASIC_REPORTS','PURCHASES','SUPPLIER_STATEMENTS','STOCK_ADJUSTMENTS','OPENING_STOCK_IMPORT','CASH_RECONCILIATION','PROFIT_REPORTS','ADVANCED_REPORTS'];
const allowedActions = Object.fromEntries([...features, 'bill.create','bill.history.read','bill.settle','products.manage','products.read','purchase.create','purchase.history.read','purchase.settle','shift.use','reports.basic','inventory.history','supplier.statement.read','cash.history'].map(key => [key, true]));
const license = { tenantId: 'SAMPLE_STORE', deploymentId: 'Sample installation', status: 'ACTIVE', planCode: 'PRO', features, maxUsers: 10, maxCounters: 5, validUntil: '2099-12-31', revision: 1, signingKeyConfigured: true };
const data = {
  '/products': products, '/products/all': products, '/salesman': [{ id: 1, employeeId: 'SAMPLE-01', name: 'Sample Salesperson', active: true }],
  '/suppliers': [{ id: 1, name: 'Sample Stationery Supplier', supplierName: 'Sample Stationery Supplier', supplierCode: 'SUP-001', active: true }, { id: 2, name: 'Sample Office Supplier', supplierName: 'Sample Office Supplier', supplierCode: 'SUP-002', active: true }],
  '/bills': bills, '/purchases': purchases, '/customers': customers.map((name, i) => ({ id: i + 1, name, customerName: name, contactInfo: `900000000${i + 1}`, phone: `900000000${i + 1}`, outstandingAmount: [250, 180, 120][i], dueAmount: [250, 180, 120][i] })),
  '/me/capabilities': { license, allowedActions }, '/gst/settings': { configured: false, registrationMode: 'UNREGISTERED', priceMode: 'EXCLUSIVE', stateCode: '27' },
  '/store-profile': { storeName: 'Sample Stationery Store', address: 'Sample address', phoneNumber: '9000000000', invoiceHeader: 'INVOICE', invoiceFooter: 'Thank you for shopping with us.' },
  '/shifts/status': { shiftId: 7, cashierName: 'Sample Cashier', counterName: 'Counter 1', openedAt: '2026-10-09T09:00:00' },
  '/dashboard/today': { todaySales: 6600, billCount: 36, customerDue: 550, supplierDue: 2000, lowStockCount: 2, lowStockThreshold: 10, lowStockItems: products.filter(p => p.stockQuantity < 10), paymentSplit: { CASH: 3000, UPI: 2350, CARD: 700, CREDIT: 550 } },
  '/reports/sales-profit': { generatedAt: '2026-10-09T18:30:00', from: '2026-10-09', to: '2026-10-09', costingBasis: 'Captured unit cost at sale', summary: profitSummary, items: profitItems, daily: [{ date: '2026-10-09', statistics: profitSummary }] },
  '/inventory/movements': { items: [{ id: 1, productId: 1, productName: products[0].name, barcode: products[0].barcode, type: 'PURCHASE', quantityChange: 50, quantityBefore: 0, quantityAfter: 50, reference: 'SUP-0101', actor: 'Sample Manager', createdAt: '2026-10-08T10:00:00' }, { id: 2, productId: 1, productName: products[0].name, barcode: products[0].barcode, type: 'SALE', quantityChange: -2, quantityBefore: 50, quantityAfter: 48, reference: 'INV-00000101', actor: 'Sample Cashier', createdAt: '2026-10-09T10:15:00' }], totalElements: 2, totalPages: 1, page: 0, size: 25 },
};
export function demoData(path) { return data[path] ?? []; }
export const demoProducts = products;
export const demoBills = bills;
export const demoPurchases = purchases;
export const demoDashboard = data['/dashboard/today'];
const readOnly = () => { const error = new Error('This is a read-only showcase with sample records. Contact us for a guided billing demonstration.'); error.response = { status: 400, data: { message: error.message } }; return Promise.reject(error); };
const api = {
  get: async (path, config = {}) => {
    if (config.signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    if (path.includes('/pdf') || path.includes('/labels')) return readOnly();
    const params = config.params || {};
    if (path === '/dashboard/today') {
      const threshold = Number(params.lowStockThreshold ?? 10);
      const lowStockItems = products.filter(p => p.stockQuantity <= threshold);
      return { data: { ...data[path], lowStockThreshold: threshold, lowStockCount: lowStockItems.length, lowStockItems } };
    }
    if (path === '/reports/sales-profit') {
      const includes = (value, filter) => !filter || String(value).toLowerCase().includes(String(filter).toLowerCase());
      const withinDate = (!params.from || params.from <= '2026-10-09') && (!params.to || params.to >= '2026-10-09');
      const items = profitItems.filter(p => withinDate && includes(p.productName, params.productName) && includes(p.barcode, params.barcode)
        && (!params.category || p.category.toLowerCase() === params.category.toLowerCase())
        && (!params.view || ['ALL', 'COMPLETE'].includes(params.view)));
      const sum = key => items.reduce((total, p) => total + p.statistics[key], 0);
      const summary = statistics(sum('netSales'), sum('costOfGoodsSold'), sum('quantitySold'), sum('saleLines'));
      return { data: { ...data[path], from: params.from || '2026-10-09', to: params.to || '2026-10-09', summary, items, daily: items.length ? [{ date: '2026-10-09', statistics: summary }] : [] } };
    }
    if (path === '/inventory/movements') {
      const items = data[path].items.filter(m => (!params.productId || m.productId === Number(params.productId)) && (!params.type || m.type === params.type)
        && (!params.from || m.createdAt.slice(0, 10) >= params.from) && (!params.to || m.createdAt.slice(0, 10) <= params.to))
        .map(m => ({ ...m, actorName: m.actor, notes: 'Fictional sample movement' }));
      return { data: { ...data[path], items, totalElements: items.length, totalPages: items.length ? 1 : 0 } };
    }
    if (path.startsWith('/products/barcode/')) return { data: products.find(p => p.barcode === decodeURIComponent(path.split('/').pop())) || null };
    if (/\/products\/\d+\/barcodes/.test(path)) return { data: products.filter(p => p.id === Number(path.split('/')[2])).map(p => ({ barcode: p.barcode, quantityPerScan: 1, primaryBarcode: true })) };
    return { data: demoData(path) };
  },
  post: async (path) => path === '/gst/quote' || path === '/gst/purchase-quote' ? { data: { configured: false } } : readOnly(),
  patch: readOnly, put: readOnly, delete: readOnly,
};
export const fetchProductByBarcode = async barcode => (await api.get(`/products/barcode/${barcode}`)).data;
export const fetchProductBarcodes = async id => (await api.get(`/products/${id}/barcodes`)).data;
export const linkProductBarcode = readOnly;
export default api;
