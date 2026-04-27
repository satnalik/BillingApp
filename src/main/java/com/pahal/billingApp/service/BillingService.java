package com.pahal.billingApp.service;


import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.entity.Salesman;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.ProductRepository;
import com.pahal.billingApp.repository.SalesManRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BillingService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private SalesManRepository salesManRepository;

    @Transactional
    public Bill createBill(Bill billRequest) {
        double taxableTotal = 0;

        // Use Integer.valueOf carefully or use a more robust parsing
        Salesman existingSalesMan = salesManRepository.findById(Integer.valueOf(billRequest.getSalesMan().getEmployeeId()))
                .orElseThrow(() -> new RuntimeException("Salesman not found"));

        for (BillItem item : billRequest.getItems()) {
            // 1. Find product
            Product product = productRepository.findByName(item.getProductName());
            if (product == null) throw new RuntimeException("Product not found: " + item.getProductName());

            // 2. Stock Check
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for: " + product.getName());
            }
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());

            // 3. Calculation Fix: (Price - (Price * Disc / 100)) * Qty
            item.setPriceAtSale(product.getPrice());
            double discountedPrice = item.getPriceAtSale() - (item.getPriceAtSale() * item.getDiscount() / 100);
            taxableTotal += (discountedPrice * item.getQuantity());
        }

        billRequest.setSalesMan(existingSalesMan);

        // 4. Apply 18% GST (9% CGST + 9% SGST) to match the Frontend
        double grandTotal = taxableTotal * 1.18;

        // Rounding to 2 decimal places before saving
        billRequest.setTotalAmount(Math.round(grandTotal * 100.0) / 100.0);

        return billRepository.save(billRequest);
    }
}
