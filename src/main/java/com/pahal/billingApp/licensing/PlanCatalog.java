package com.pahal.billingApp.licensing;

import java.util.*;

/** Version one is immutable; existing signed grants never depend on later catalog edits. */
public final class PlanCatalog {
    private PlanCatalog() {}
    public record Plan(String code, int version, Set<Feature> features, int suggestedUsers, int suggestedCounters) {}
    public static List<Plan> plans() {
        var basic = EnumSet.copyOf(Feature.CORE);
        var plus = EnumSet.copyOf(basic);
        plus.addAll(Set.of(Feature.PURCHASES, Feature.SUPPLIER_STATEMENTS, Feature.STOCK_ADJUSTMENTS,
                Feature.OPENING_STOCK_IMPORT, Feature.CASH_RECONCILIATION));
        var pro = EnumSet.allOf(Feature.class);
        return List.of(new Plan("BASIC", 1, Set.copyOf(basic), 2, 1),
                new Plan("PLUS", 1, Set.copyOf(plus), 5, 2),
                new Plan("PRO", 1, Set.copyOf(pro), 10, 5),
                new Plan("PLUS_PRO", 1, Set.copyOf(pro), 25, 10));
    }
    public static Plan plan(String code) {
        return plans().stream().filter(p -> p.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan."));
    }
    public static void validate(Set<Feature> features) {
        if (features == null || features.stream().anyMatch(Objects::isNull) || !features.containsAll(Feature.CORE))
            throw new IllegalArgumentException("Every license must include the core billing features.");
        for (var feature : features) if (!features.containsAll(feature.dependencies()))
            throw new IllegalArgumentException(feature + " requires " + feature.dependencies());
    }
}
