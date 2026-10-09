package com.pahal.billingApp.service;

import com.pahal.billingApp.entity.GstDocument;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Decimal arithmetic; the final discount is an absolute reduction in the gross payable amount. */
public final class GstCalculator {
    private GstCalculator() {}
    public static final Set<String> CATEGORIES = Set.of("TAXABLE", "NIL_RATED", "EXEMPT", "NON_GST");
    public static final Set<String> UNITS = Set.of("BAG", "BAL", "BDL", "BKL", "BOU", "BOX", "BTL", "BUN", "CAN", "CBM", "CCM", "CMS", "CTN", "DOZ", "DRM", "GGK", "GMS", "GRS", "GYD", "KGS", "KLR", "KME", "LTR", "MTR", "MTS", "NOS", "PAC", "PCS", "PRS", "QTL", "ROL", "SET", "SQF", "SQM", "SQY", "TBS", "TGM", "THD", "TON", "TUB", "UGS", "UNT", "YDS", "OTH");
    public record Input(Long sourceItemId, Long productId, String productName, String barcode,
            double quantity, double price, double itemDiscountPercent, String hsnCode,
            String unitCode, String taxCategory, Double gstRate) {}
    public record Result(List<GstDocument.Line> lines, BigDecimal taxable, BigDecimal cgst,
            BigDecimal sgst, BigDecimal igst, BigDecimal total, BigDecimal beforeDiscount) {
        public BigDecimal tax() { return cgst.add(sgst).add(igst); }
    }

    public static Result calculate(List<Input> inputs, double finalDiscount, String priceMode,
            boolean registered, boolean interstate) {
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("At least one tax line is required.");
        if (!Set.of("INCLUSIVE", "EXCLUSIVE").contains(priceMode)) throw new IllegalArgumentException("Choose inclusive or exclusive pricing.");
        List<BigDecimal> gross = new ArrayList<>(), rates = new ArrayList<>();
        for (Input input : inputs) {
            BigDecimal qty = decimal(input.quantity()), price = decimal(input.price());
            BigDecimal discount = decimal(input.itemDiscountPercent());
            if (qty.signum() <= 0 || price.signum() < 0 || discount.signum() < 0 || discount.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("Invalid quantity, price or discount for " + input.productName());
            BigDecimal rate = registered ? rate(input.taxCategory(), input.gstRate(), input.hsnCode(), input.unitCode()) : BigDecimal.ZERO;
            rates.add(rate);
            BigDecimal value = money(qty.multiply(price).multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2))));
            gross.add("EXCLUSIVE".equals(priceMode) ? value.add(money(value.multiply(rate))) : value);
        }
        BigDecimal before = gross.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (before.compareTo(new BigDecimal("1000000000")) > 0) throw new IllegalArgumentException("Invoice value exceeds the supported amount limit.");
        BigDecimal discount = money(decimal(finalDiscount));
        if (discount.signum() < 0 || discount.compareTo(before) > 0) throw new IllegalArgumentException("Final discount cannot exceed the invoice value.");
        List<BigDecimal> shares = allocate(gross, discount);
        List<GstDocument.Line> lines = new ArrayList<>();
        BigDecimal taxable = money(BigDecimal.ZERO), cgst = taxable, sgst = taxable, igst = taxable;
        for (int index = 0; index < inputs.size(); index++) {
            Input input = inputs.get(index);
            BigDecimal value = gross.get(index).subtract(shares.get(index));
            BigDecimal base = value.divide(BigDecimal.ONE.add(rates.get(index)), 2, RoundingMode.HALF_UP);
            BigDecimal tax = value.subtract(base);
            GstDocument.Line line = new GstDocument.Line();
            line.setSourceItemId(input.sourceItemId()); line.setProductId(input.productId());
            line.setProductName(input.productName()); line.setBarcode(input.barcode());
            line.setHsnCode(registered || input.hsnCode() != null && input.hsnCode().matches("(?:[0-9]{4}|[0-9]{6}|[0-9]{8})") ? input.hsnCode() : null);
            line.setUnitCode(registered || UNITS.contains(input.unitCode() == null ? "" : input.unitCode()) ? input.unitCode() : null);
            line.setTaxCategory(registered ? input.taxCategory() : "NON_GST");
            line.setQuantity(money(decimal(input.quantity()))); line.setGstRate(rates.get(index));
            line.setTaxableAmount(base); line.setDiscountAmount(shares.get(index));
            line.setCgstAmount(interstate ? money(BigDecimal.ZERO) : money(tax.divide(new BigDecimal("2"))));
            line.setSgstAmount(interstate ? money(BigDecimal.ZERO) : tax.subtract(line.getCgstAmount()));
            line.setIgstAmount(interstate ? tax : money(BigDecimal.ZERO));
            taxable = taxable.add(base); cgst = cgst.add(line.getCgstAmount());
            sgst = sgst.add(line.getSgstAmount()); igst = igst.add(line.getIgstAmount());
            lines.add(line);
        }
        return new Result(lines, taxable, cgst, sgst, igst, before.subtract(discount), before);
    }

    public static BigDecimal rate(String category, Double rate, String hsn, String unit) {
        if (!CATEGORIES.contains(category == null ? "" : category)) throw new IllegalArgumentException("Set the product's tax classification before GST billing.");
        if (hsn == null || !hsn.matches("(?:[0-9]{4}|[0-9]{6}|[0-9]{8})")) throw new IllegalArgumentException("Enter a 4, 6 or 8 digit HSN for GST billing.");
        if (!UNITS.contains(unit == null ? "" : unit)) throw new IllegalArgumentException("Choose a valid unit code for GST billing.");
        if (rate == null || !Double.isFinite(rate) || rate < 0 || rate > 1) throw new IllegalArgumentException("Set a GST rate between 0 and 100 percent.");
        if ("TAXABLE".equals(category) && rate == 0 || !"TAXABLE".equals(category) && rate != 0)
            throw new IllegalArgumentException("Taxable items need a positive GST rate; nil-rated, exempt and non-GST items need a zero rate.");
        BigDecimal decimalRate = BigDecimal.valueOf(rate);
        if (decimalRate.stripTrailingZeros().scale() > 6) throw new IllegalArgumentException("GST rate supports up to four decimal places as a percentage.");
        return decimalRate;
    }

    public static List<BigDecimal> allocate(List<BigDecimal> weights, BigDecimal total) {
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> result = new ArrayList<>(), remainders = new ArrayList<>();
        BigDecimal assigned = BigDecimal.ZERO, cents = money(total).movePointRight(2);
        for (BigDecimal weight : weights) {
            BigDecimal numerator = cents.multiply(weight);
            BigDecimal floor = sum.signum() == 0 ? BigDecimal.ZERO : numerator.divide(sum, 0, RoundingMode.DOWN);
            result.add(floor.movePointLeft(2)); assigned = assigned.add(floor);
            remainders.add(numerator.subtract(floor.multiply(sum)));
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < weights.size(); index++) order.add(index);
        order.sort(Comparator.<Integer, BigDecimal>comparing(remainders::get).reversed().thenComparingInt(i -> i));
        int leftover = cents.subtract(assigned).intValueExact();
        if (sum.signum() == 0 && leftover != 0) throw new IllegalArgumentException("Cannot allocate a discount across zero-value items.");
        for (int index = 0; index < leftover; index++) {
            int target = order.get(index); result.set(target, result.get(target).add(new BigDecimal("0.01")));
        }
        return result;
    }
    public static BigDecimal decimal(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000_000d) throw new IllegalArgumentException("Tax amounts and quantities must be finite and within supported limits.");
        return BigDecimal.valueOf(value);
    }
    public static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
