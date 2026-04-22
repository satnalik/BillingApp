package com.pahal.billingApp.service;


import com.pahal.billingApp.entity.Bill;
import com.pahal.billingApp.entity.BillItem;
import com.pahal.billingApp.entity.Product;
import com.pahal.billingApp.repository.BillRepository;
import com.pahal.billingApp.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BillRepository billRepository;

    @Transactional // Ensures either everything saves or nothing saves
    public Bill createBill(Bill billRequest) {
        double total = 0;

        for (BillItem item : billRequest.getItems()) {
            // 1. Find the product (Automatically filtered by Tenant via AOP)
            Product product = productRepository.findByName(item.getProductName());

            // 2. Check and Update Stock
            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException("Not enough stock for: " + product.getName());
            }
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());

            // 3. Set price and calculate total
            item.setPriceAtSale(product.getPrice());
            total += ((item.getPriceAtSale()- ((item.getPriceAtSale() * item.getDiscount())/100) * item.getQuantity()));
        }

        billRequest.setTotalAmount(total);
        return billRepository.save(billRequest);
    }
}
