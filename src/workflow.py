"""Процедурные операции приёмки; состояние передаётся параметром data"""

from algorithms import compare_delivery_items, select_storage_cell
from storage import find_record, new_id, require_date, require_quantity


def delivery_items(data, delivery_id):
    find_record(data, "deliveries", "delivery_id", delivery_id)
    return [
        item for item in data["delivery_items"] if item["delivery_id"] == delivery_id
    ]


def expected_items(data, delivery_id):
    return [
        item
        for item in delivery_items(data, delivery_id)
        if item["expected_quantity"] > 0
    ]


def validate_items(data, items, quantity_key, allow_empty=False):
    if not isinstance(items, list) or (not items and not allow_empty):
        raise ValueError("Нужен список позиций.")
    seen = set()
    for item in items:
        product_id = item["product_id"]
        find_record(data, "products", "product_id", product_id)
        require_quantity(item[quantity_key], 1)
        if product_id in seen:
            raise ValueError(
                "Объедините повторные позиции одного товара до сопоставления."
            )
        seen.add(product_id)


def register_expected_delivery(data, delivery):
    find_record(data, "clients", "client_id", delivery["client_id"])
    require_date(delivery["expected_date"])
    validate_items(data, delivery["items"], "expected_quantity")
    delivery_id = new_id(data, "deliveries", "delivery_id", "D")
    result = {
        "delivery_id": delivery_id,
        "client_id": delivery["client_id"],
        "expected_date": delivery["expected_date"],
        "actual_date": None,
        "status": "ожидается",
    }
    data["deliveries"].append(result)
    for item in delivery["items"]:
        data["delivery_items"].append(
            {
                "delivery_item_id": new_id(
                    data, "delivery_items", "delivery_item_id", "I"
                ),
                "delivery_id": delivery_id,
                "product_id": item["product_id"],
                "expected_quantity": item["expected_quantity"],
                "actual_quantity": None,
            }
        )
    return result


def record_actual_receipt(data, delivery_id, actual_items, actual_date):
    delivery = find_record(data, "deliveries", "delivery_id", delivery_id)
    if delivery["status"] != "ожидается":
        raise ValueError(
            "Фактическое поступление уже сохранено; повторный ввод запрещён."
        )
    require_date(actual_date)
    validate_items(data, actual_items, "actual_quantity", allow_empty=True)
    items = delivery_items(data, delivery_id)
    for item in items:
        item["actual_quantity"] = 0
    for actual in actual_items:
        found = next(
            (item for item in items if item["product_id"] == actual["product_id"]), None
        )
        if found is None:
            found = {
                "delivery_item_id": new_id(
                    data, "delivery_items", "delivery_item_id", "I"
                ),
                "delivery_id": delivery_id,
                "product_id": actual["product_id"],
                "expected_quantity": 0,
                "actual_quantity": 0,
            }
            data["delivery_items"].append(found)
            items.append(found)
        found["actual_quantity"] = actual["actual_quantity"]
    delivery["actual_date"] = actual_date
    delivery["status"] = "поступила"
    return [item["delivery_item_id"] for item in items]


def receipt_discrepancies(data, delivery_id):
    items = delivery_items(data, delivery_id)
    if any(item["actual_quantity"] is None for item in items):
        raise ValueError("Сначала сохраните фактическое поступление: пункт 2.")
    actual = [item for item in items if item["actual_quantity"] > 0]
    return compare_delivery_items(expected_items(data, delivery_id), actual)


def register_discrepancies(data, delivery_id, discrepancies):
    delivery = find_record(data, "deliveries", "delivery_id", delivery_id)
    if delivery["status"] not in ("поступила", "проверена", "требует согласования"):
        raise ValueError(
            "Расхождения регистрируются после поступления и до принятия партий."
        )
    quantities = receipt_discrepancies(data, delivery_id)
    supplied = [
        {k: d[k] for k in ("product_id", "discrepancy_type", "quantity_difference")}
        for d in discrepancies
        if d["discrepancy_type"] != "качество"
    ]
    if supplied != quantities:
        raise ValueError(
            "Количественные расхождения должны совпадать с результатом алгоритма."
        )
    items = delivery_items(data, delivery_id)
    item_ids = {item["delivery_item_id"] for item in items}
    for entry in discrepancies:
        if entry["discrepancy_type"] == "качество":
            if (
                not entry.get("description", "").strip()
                or entry["quantity_difference"] != 0
            ):
                raise ValueError(
                    "Качественное отклонение требует описания и нулевой разности."
                )
        if not any(item["product_id"] == entry["product_id"] for item in items):
            raise ValueError("Расхождение не связано с позицией поставки.")
    data["discrepancies"][:] = [
        d for d in data["discrepancies"] if d["delivery_item_id"] not in item_ids
    ]
    result = []
    for entry in discrepancies:
        item = next(item for item in items if item["product_id"] == entry["product_id"])
        record = {
            "discrepancy_id": new_id(data, "discrepancies", "discrepancy_id", "R"),
            "delivery_item_id": item["delivery_item_id"],
            "discrepancy_type": entry["discrepancy_type"],
            "quantity_difference": entry["quantity_difference"],
            "description": entry.get("description", ""),
        }
        data["discrepancies"].append(record)
        result.append(record)
    delivery["status"] = "требует согласования" if result else "проверена"
    return result


def accept_delivery(data, delivery_id, batch_details, approved):
    """После ручного согласования фиксируются принятые количества партий."""
    delivery = find_record(data, "deliveries", "delivery_id", delivery_id)
    if delivery["status"] not in ("проверена", "требует согласования"):
        raise ValueError("Сначала зарегистрируйте результаты приёмки: пункт 3.")
    if approved is not True:
        raise ValueError(
            "Нужно подтверждение приёмки и урегулирования критических отклонений."
        )
    items = [
        item
        for item in delivery_items(data, delivery_id)
        if item["actual_quantity"] > 0
    ]
    if not items:
        raise ValueError("Нет фактически поступившего товара для принятия.")
    if not batch_details or not set(batch_details) <= {
        item["delivery_item_id"] for item in items
    }:
        raise ValueError("Укажите хотя бы одну партию фактически поступившей позиции.")
    for item_id, detail in batch_details.items():
        if (
            not isinstance(detail["batch_number"], str)
            or not detail["batch_number"].strip()
        ):
            raise ValueError("Укажите номер партии.")
        require_date(detail["expiration_date"], optional=True)
        require_quantity(detail["quantity"], 1)
        item = next(i for i in items if i["delivery_item_id"] == item_id)
        if detail["quantity"] > item["actual_quantity"]:
            raise ValueError("Принятое количество не может превышать фактическое.")
    result = []
    for item_id, detail in batch_details.items():
        batch = {
            "batch_id": new_id(data, "batches", "batch_id", "B"),
            "delivery_item_id": item_id,
            "quantity": detail["quantity"],
            "batch_number": detail["batch_number"],
            "expiration_date": detail["expiration_date"],
        }
        data["batches"].append(batch)
        result.append(batch)
    delivery["status"] = "принята"
    return result


def batch_remaining(data, batch_id):
    batch = find_record(data, "batches", "batch_id", batch_id)
    placed = sum(p["quantity"] for p in data["placements"] if p["batch_id"] == batch_id)
    return batch["quantity"] - placed


def register_placement(data, placement, required_zone=None):
    if any(p["placement_id"] == placement["placement_id"] for p in data["placements"]):
        raise ValueError("Это размещение уже зарегистрировано.")
    batch = find_record(data, "batches", "batch_id", placement["batch_id"])
    item = find_record(
        data, "delivery_items", "delivery_item_id", batch["delivery_item_id"]
    )
    delivery = find_record(data, "deliveries", "delivery_id", item["delivery_id"])
    if delivery["status"] != "принята":
        raise ValueError("Поставка должна быть принята до размещения.")
    require_quantity(placement["quantity"], 1)
    require_date(placement["placement_date"])
    if placement["placement_date"] < delivery["actual_date"]:
        raise ValueError("Размещение не может предшествовать поступлению.")
    remaining = batch_remaining(data, batch["batch_id"])
    if remaining <= 0 or placement["quantity"] != remaining:
        raise ValueError(
            "Разместите всю оставшуюся партию; повторное размещение запрещено."
        )
    product = dict(find_record(data, "products", "product_id", item["product_id"]))
    if required_zone is not None and product["allowed_zone"] not in (
        None,
        required_zone,
    ):
        raise ValueError("Ограничение операции противоречит зоне товара.")
    if required_zone is not None:
        product["allowed_zone"] = required_zone
    cell = find_record(data, "storage_cells", "cell_id", placement["cell_id"])
    if select_storage_cell(product, placement["quantity"], [cell]) is None:
        raise ValueError(
            "Ячейка недоступна, не подходит по режиму/зоне или недостаточно места."
        )
    record = {
        key: placement[key]
        for key in ("placement_id", "batch_id", "cell_id", "quantity", "placement_date")
    }
    data["placements"].append(record)
    cell["available_capacity"] -= placement["quantity"]
    own_batches = [
        b
        for b in data["batches"]
        if b["delivery_item_id"]
        in {i["delivery_item_id"] for i in delivery_items(data, item["delivery_id"])}
    ]
    if all(batch_remaining(data, b["batch_id"]) == 0 for b in own_batches):
        delivery["status"] = "размещена"
    return record
