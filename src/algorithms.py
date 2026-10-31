def compare_delivery_items(expected_items, actual_items):
    """O(n*m+n+m); O(1) рабочей памяти, O(n+m) для результата."""
    discrepancies = []
    for expected in expected_items:  # C1 / A04
        actual_quantity = 0
        for actual in actual_items:  # C2 / A06
            if actual["product_id"] == expected["product_id"]:
                actual_quantity = actual["actual_quantity"]
                break
        difference = actual_quantity - expected["expected_quantity"]
        if difference < 0:
            kind = "недостача"
        elif difference > 0:
            kind = "излишек"
        else:
            continue
        discrepancies.append(
            {
                "product_id": expected["product_id"],
                "discrepancy_type": kind,
                "quantity_difference": difference,
            }
        )
    for actual in actual_items:  # C3 / A16
        found = False
        for expected in expected_items:  # C4 / A18
            if actual["product_id"] == expected["product_id"]:
                found = True
                break
        if not found:
            discrepancies.append(
                {
                    "product_id": actual["product_id"],
                    "discrepancy_type": "неожиданный товар",
                    "quantity_difference": actual["actual_quantity"],
                }
            )
    return discrepancies


def select_storage_cell(product, quantity, storage_cells):
    """Best fit: O(k) времени, O(1) памяти; при равенстве первая ячейка."""
    best_cell = None
    best_remaining = None
    required_zone = product.get("allowed_zone")
    for cell in storage_cells:  # C5 / B04
        if cell["status"] != "доступна":
            continue
        if cell["storage_conditions"] != product["storage_conditions"]:
            continue
        if required_zone is not None:
            if cell["zone"] != required_zone:
                continue
        remaining = cell["available_capacity"] - quantity
        if remaining < 0:
            continue
        if best_cell is None:
            best_cell = cell
            best_remaining = remaining
        elif remaining < best_remaining:
            best_cell = cell
            best_remaining = remaining
    return best_cell
