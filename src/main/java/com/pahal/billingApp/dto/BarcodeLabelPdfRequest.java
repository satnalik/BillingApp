package com.pahal.billingApp.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BarcodeLabelPdfRequest {
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long productId;
        private Integer labelsToPrint;
    }
}
