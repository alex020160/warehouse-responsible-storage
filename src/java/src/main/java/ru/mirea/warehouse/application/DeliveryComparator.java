package ru.mirea.warehouse.application;

import java.math.BigInteger;
import java.util.*;
import ru.mirea.warehouse.domain.DeliveryItem;

public final class DeliveryComparator {
    /** O(n*m+n+m), рабочая память O(1), результат O(n+m). */
    public List<Map<String, Object>> compareDeliveryItems(List<DeliveryItem> expectedItems, List<DeliveryItem> actualItems) {
        List<Map<String, Object>> discrepancies = new ArrayList<>();
        for (DeliveryItem expected : expectedItems) {
            BigInteger actualQuantity = BigInteger.ZERO;
            for (DeliveryItem actual : actualItems) {
                if (actual.getProductId().equals(expected.getProductId())) {
                    actualQuantity = actual.getActualQuantity();
                    break;
                }
            }
            BigInteger difference = actualQuantity.subtract(expected.getExpectedQuantity());
            String kind;
            if (difference.signum() < 0) kind = "недостача";
            else if (difference.signum() > 0) kind = "излишек";
            else continue;
            discrepancies.add(deviation(expected.getProductId(), kind, difference));
        }
        for (DeliveryItem actual : actualItems) {
            boolean found = false;
            for (DeliveryItem expected : expectedItems) {
                if (actual.getProductId().equals(expected.getProductId())) { found = true; break; }
            }
            if (!found) discrepancies.add(deviation(actual.getProductId(), "неожиданный товар", actual.getActualQuantity()));
        }
        return discrepancies;
    }
    private Map<String, Object> deviation(String productId, String kind, BigInteger difference) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("product_id", productId);
        result.put("discrepancy_type", kind);
        result.put("quantity_difference", difference);
        return result;
    }
}
