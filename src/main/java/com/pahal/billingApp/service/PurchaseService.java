package com.pahal.billingApp.service;

import com.pahal.billingApp.context.TenantContext;
import com.pahal.billingApp.dto.AddPurchasePaymentRequest;
import com.pahal.billingApp.dto.CancelPurchaseBillRequest;
import com.pahal.billingApp.dto.CreatePurchaseBillItemRequest;
import com.pahal.billingApp.dto.CreatePurchaseBillRequest;
import com.pahal.billingApp.dto.PurchaseBarcodeLabelResponse;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.ProductBarcode;
import com.pahal.billingApp.entity.PurchaseBill;
import com.pahal.billingApp.entity.PurchaseBillItem;
import com.pahal.billingApp.entity.PurchasePayment;
import com.pahal.billingApp.entity.Supplier;
import com.pahal.billingApp.enums.PaymentMethod;
import com.pahal.billingApp.enums.PurchaseStatus;
import com.pahal.billingApp.repository.ProductBarcodeRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.PurchaseBillRepository;
import com.pahal.billingApp.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PurchaseService {

    @Autowired
    private PurchaseBillRepository purchaseBillRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductBarcodeRepository productBarcodeRepository;

    @Autowired
    private BarcodeService barcodeService;

    @Transactional
    public PurchaseBill createPurchaseBill(CreatePurchaseBillRequest request) {
        if (request == null) throw new RuntimeException("Request is required");
        if (request.getSupplierId() == null) throw new RuntimeException("Supplier is required");
        if (request.getBillNumber() == null || request.getBillNumber().isBlank()) {
            throw new RuntimeException("Bill number is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("At least one purchase item is required");
        }

        String tenantId = TenantContext.getCurrentTenant();
        Supplier supplier = findSupplier(request.getSupplierId(), tenantId);

        PurchaseBill bill = new PurchaseBill();
        bill.setSupplier(supplier);
        bill.setBillNumber(request.getBillNumber().trim());
        bill.setBillDate(request.getBillDate() != null ? request.getBillDate() : LocalDate.now());
        bill.setDiscountAmount(round2(nonNull(request.getDiscountAmount())));
        bill.setTaxAmount(round2(nonNull(request.getTaxAmount())));
        bill.setNotes(request.getNotes());

        double subTotal = 0.0;
        List<PurchaseBillItem> items = new ArrayList<>();
        for (CreatePurchaseBillItemRequest itemRequest : request.getItems()) {
            PurchaseBillItem item = buildItem(itemRequest, tenantId);
            item.setPurchaseBill(bill);
            items.add(item);
            subTotal += nonNull(item.getLineTotal());

            Product product = item.getProduct();
            product.setStockQuantity(nonNull(product.getStockQuantity()) + item.getQuantity());
            product.setCostPrice(item.getPurchasePrice());
            if (item.getSellingPrice() != null) {
                product.setSellingPrice(item.getSellingPrice());
                product.setPrice(item.getSellingPrice());
            }
            if (product.getSupplier() == null) {
                product.setSupplier(supplier);
                product.setSupplierName(supplier.getName());
            }
        }

        double total = round2(subTotal - bill.getDiscountAmount() + bill.getTaxAmount());
        if (total < 0.0) {
            throw new RuntimeException("Purchase total cannot be negative");
        }

        bill.setSubTotalAmount(round2(subTotal));
        bill.setTotalAmount(total);

        applyPayment(request, bill);
        bill.setItems(items);

        return purchaseBillRepository.save(bill);
    }

    @Transactional(readOnly = true)
    public List<PurchaseBill> getAllPurchaseBills() {
        return purchaseBillRepository.findAllByOrderByBillDateDescIdDesc();
    }

    @Transactional(readOnly = true)
    public PurchaseBill getPurchaseBill(Long id) {
        return purchaseBillRepository.findWithDetailsById(id)
                .orElseThrow(() -> new RuntimeException("Purchase bill not found"));
    }

    @Transactional
    public PurchaseBill cancelPurchaseBill(Long id, CancelPurchaseBillRequest request) {
        if (id == null) throw new RuntimeException("Purchase bill id is required");

        PurchaseBill bill = purchaseBillRepository.findWithDetailsByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Purchase bill not found"));

        if (getEffectiveStatus(bill) == PurchaseStatus.CANCELLED) {
            throw new RuntimeException("Purchase bill is already cancelled");
        }
        if (nonNull(bill.getPaidAmount()) > 0.0001) {
            throw new RuntimeException("Cannot cancel purchase after supplier payment is recorded");
        }

        if (bill.getItems() != null) {
            for (PurchaseBillItem item : bill.getItems()) {
                Product product = item.getProduct();
                if (product == null) continue;

                double currentStock = nonNull(product.getStockQuantity());
                double reverseQuantity = nonNull(item.getQuantity());
                if (currentStock - reverseQuantity < -0.0001) {
                    throw new RuntimeException("Cannot cancel purchase because stock would become negative for: "
                            + product.getName());
                }
                product.setStockQuantity(round2(currentStock - reverseQuantity));
            }
        }

        bill.setStatus(PurchaseStatus.CANCELLED);
        bill.setCancelReason(request != null ? request.getReason() : null);
        bill.setCancelledAt(LocalDateTime.now());
        return purchaseBillRepository.save(bill);
    }

    @Transactional
    public PurchaseBill addDuePayment(Long purchaseBillId, AddPurchasePaymentRequest request) {
        if (purchaseBillId == null) throw new RuntimeException("Purchase bill id is required");
        if (request == null) throw new RuntimeException("Request is required");
        if (request.getMethod() == null) throw new RuntimeException("Payment method is required");
        if (request.getMethod() == PaymentMethod.CREDIT) {
            throw new RuntimeException("CREDIT cannot be used to pay supplier due");
        }
        if (request.getAmount() == null || request.getAmount() <= 0.0) {
            throw new RuntimeException("Payment amount must be > 0");
        }

        PurchaseBill bill = purchaseBillRepository.findWithDetailsByIdForUpdate(purchaseBillId)
                .orElseThrow(() -> new RuntimeException("Purchase bill not found"));

        if (getEffectiveStatus(bill) == PurchaseStatus.CANCELLED) {
            throw new RuntimeException("Cannot add payment to a cancelled purchase bill");
        }

        double currentDue = nonNull(bill.getDueAmount());
        double amount = request.getAmount();
        if (amount - currentDue > 0.0001) {
            throw new RuntimeException("Payment amount exceeds current supplier due");
        }

        PurchasePayment payment = new PurchasePayment();
        payment.setPurchaseBill(bill);
        payment.setMethod(request.getMethod());
        payment.setAmount(round2(amount));
        payment.setReference(request.getReference());
        bill.getPayments().add(payment);

        bill.setPaidAmount(round2(nonNull(bill.getPaidAmount()) + amount));
        bill.setDueAmount(round2(currentDue - amount));

        return purchaseBillRepository.save(bill);
    }

    @Transactional
    public PurchaseBarcodeLabelResponse generateBarcodeLabels(Long purchaseBillId) {
        PurchaseBill bill = purchaseBillRepository.findWithDetailsById(purchaseBillId)
                .orElseThrow(() -> new RuntimeException("Purchase bill not found"));

        if (getEffectiveStatus(bill) == PurchaseStatus.CANCELLED) {
            throw new RuntimeException("Cannot generate barcodes for a cancelled purchase bill");
        }

        Supplier supplier = bill.getSupplier();
        PurchaseBarcodeLabelResponse response = new PurchaseBarcodeLabelResponse();
        response.setPurchaseBillId(bill.getId());
        response.setBillNumber(bill.getBillNumber());
        if (supplier != null) {
            response.setSupplierId(supplier.getId());
            response.setSupplierName(supplier.getName());
            response.setSupplierCode(supplier.getSupplierCode());
        }

        response.setItems(bill.getItems().stream()
                .map(item -> toBarcodeLabelItem(item, supplier))
                .toList());
        return response;
    }

    private PurchaseBarcodeLabelResponse.Item toBarcodeLabelItem(PurchaseBillItem item, Supplier supplier) {
        Product product = item.getProduct();
        if (product == null) {
            throw new RuntimeException("Purchase item product is required for barcode generation");
        }

        ProductBarcode primaryBarcode = productBarcodeRepository
                .findFirstByProductIdAndPrimaryBarcodeTrueOrderByIdAsc(product.getId())
                .orElse(null);
        String barcode = item.getBarcode();
        if (barcode == null || barcode.isBlank()) {
            barcode = barcodeService.generateProductBarcode(product, supplier);
            product.setBarcode(barcode);
            primaryBarcode = createPrimaryBarcode(product, barcode);
        } else if (primaryBarcode == null) {
            primaryBarcode = createPrimaryBarcode(product, barcode);
        }
        if (product.getSupplier() == null && supplier != null) {
            product.setSupplier(supplier);
            product.setSupplierName(supplier.getName());
        }

        PurchaseBarcodeLabelResponse.Item response = new PurchaseBarcodeLabelResponse.Item();
        response.setProductId(product.getId());
        response.setProductName(item.getProductName() != null ? item.getProductName() : product.getName());
        response.setBarcode(primaryBarcode != null ? primaryBarcode.getBarcode() : barcode);
        response.setQuantityReceived(item.getQuantity());
        response.setLabelsToPrint(toLabelCount(item.getQuantity()));
        response.setSellingPrice(item.getSellingPrice() != null ? item.getSellingPrice() : product.getSellingPrice());
        response.setMrp(product.getMrp());
        response.setCategory(product.getCategory());
        response.setSupplierName(supplier != null ? supplier.getName() : product.getSupplierName());
        return response;
    }

    private ProductBarcode createPrimaryBarcode(Product product, String barcode) {
        ProductBarcode existing = productBarcodeRepository.findByBarcode(barcode).orElse(null);
        if (existing != null) {
            existing.setPrimaryBarcode(true);
            if (existing.getQuantityPerScan() == null || existing.getQuantityPerScan() <= 0.0) {
                existing.setQuantityPerScan(1.0);
            }
            return productBarcodeRepository.save(existing);
        }

        ProductBarcode productBarcode = new ProductBarcode();
        productBarcode.setProduct(product);
        productBarcode.setBarcode(barcode);
        productBarcode.setQuantityPerScan(1.0);
        productBarcode.setPrimaryBarcode(true);
        return productBarcodeRepository.save(productBarcode);
    }

    private int toLabelCount(Double quantity) {
        if (quantity == null || quantity <= 0.0) return 0;
        return (int) Math.ceil(quantity);
    }

    private PurchaseBillItem buildItem(CreatePurchaseBillItemRequest itemRequest, String tenantId) {
        if (itemRequest == null) throw new RuntimeException("Purchase item is required");
        if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0.0) {
            throw new RuntimeException("Quantity must be > 0");
        }
        if (itemRequest.getPurchasePrice() == null || itemRequest.getPurchasePrice() < 0.0) {
            throw new RuntimeException("Purchase price must be >= 0");
        }

        PurchaseProductResolution resolution = resolvePurchaseProduct(itemRequest, tenantId);
        Product product = resolution.product();
        double quantity = round2(itemRequest.getQuantity() * resolution.quantityPerScan());
        double lineTotal = round2(quantity * itemRequest.getPurchasePrice());

        PurchaseBillItem item = new PurchaseBillItem();
        item.setProduct(product);
        item.setBarcode(resolution.barcodeUsed());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setPurchasePrice(itemRequest.getPurchasePrice());
        item.setSellingPrice(itemRequest.getSellingPrice());
        item.setLineTotal(lineTotal);
        return item;
    }

    private PurchaseProductResolution resolvePurchaseProduct(CreatePurchaseBillItemRequest itemRequest, String tenantId) {
        String barcode = blankToNull(itemRequest.getBarcode());
        Product product = null;
        double quantityPerScan = normalizeQuantityPerScan(itemRequest.getQuantityPerScan());

        if (barcode != null) {
            ProductBarcode productBarcode = productBarcodeRepository.findByBarcode(barcode).orElse(null);
            if (productBarcode != null) {
                product = productBarcode.getProduct();
                quantityPerScan = normalizeQuantityPerScan(productBarcode.getQuantityPerScan());
            } else if (itemRequest.getProductId() != null) {
                product = findProduct(itemRequest.getProductId(), tenantId);
                productBarcode = new ProductBarcode();
                productBarcode.setProduct(product);
                productBarcode.setBarcode(barcode);
                productBarcode.setQuantityPerScan(quantityPerScan);
                productBarcode.setPrimaryBarcode(false);
                productBarcodeRepository.save(productBarcode);
            } else {
                product = productRepository.findByBarcode(barcode);
                if (product != null) {
                    productBarcode = new ProductBarcode();
                    productBarcode.setProduct(product);
                    productBarcode.setBarcode(barcode);
                    productBarcode.setQuantityPerScan(quantityPerScan);
                    productBarcode.setPrimaryBarcode(false);
                    productBarcodeRepository.save(productBarcode);
                }
            }
            if (product == null) {
                throw new RuntimeException("Product not found for barcode: " + barcode);
            }
            if (itemRequest.getProductId() != null && !itemRequest.getProductId().equals(product.getId())) {
                throw new RuntimeException("Barcode does not belong to requested product");
            }
            return new PurchaseProductResolution(product, barcode, quantityPerScan);
        }

        if (itemRequest.getProductId() == null) {
            throw new RuntimeException("Product is required");
        }
        product = findProduct(itemRequest.getProductId(), tenantId);
        return new PurchaseProductResolution(product, null, quantityPerScan);
    }

    private void applyPayment(CreatePurchaseBillRequest request, PurchaseBill bill) {
        double paid = round2(nonNull(request.getPaidAmount()));
        if (paid < 0.0) throw new RuntimeException("Paid amount must be >= 0");
        if (paid - bill.getTotalAmount() > 0.0001) {
            throw new RuntimeException("Paid amount cannot exceed purchase total");
        }

        bill.setPaidAmount(paid);
        bill.setDueAmount(round2(bill.getTotalAmount() - paid));

        if (paid > 0.0001) {
            PaymentMethod method = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH;
            if (method == PaymentMethod.CREDIT) {
                throw new RuntimeException("Use paidAmount 0 for credit purchase instead of CREDIT payment method");
            }
            PurchasePayment payment = new PurchasePayment();
            payment.setPurchaseBill(bill);
            payment.setMethod(method);
            payment.setAmount(paid);
            payment.setReference(request.getPaymentReference());
            bill.getPayments().add(payment);
        }
    }

    private Supplier findSupplier(Long supplierId, String tenantId) {
        if (tenantId == null) {
            return supplierRepository.findById(supplierId)
                    .orElseThrow(() -> new RuntimeException("Supplier not found"));
        }
        return supplierRepository.findByIdAndTenantId(supplierId, tenantId)
                .orElseThrow(() -> new RuntimeException("Supplier not found"));
    }

    private Product findProduct(Long productId, String tenantId) {
        if (tenantId == null) {
            return productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
        }
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public PurchaseStatus getEffectiveStatus(PurchaseBill bill) {
        return bill != null && bill.getStatus() != null ? bill.getStatus() : PurchaseStatus.ACTIVE;
    }

    private static double nonNull(Double amount) {
        return amount != null ? amount : 0.0;
    }

    private static double round2(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static double normalizeQuantityPerScan(Double quantityPerScan) {
        if (quantityPerScan == null) {
            return 1.0;
        }
        if (quantityPerScan <= 0.0) {
            throw new RuntimeException("Quantity per scan must be > 0");
        }
        return quantityPerScan;
    }

    private record PurchaseProductResolution(Product product, String barcodeUsed, double quantityPerScan) {
    }
}
