"""Восемь коллекций ER в одном атомарно заменяемом JSON-файле"""

import json
import os
import tempfile
from datetime import date
from pathlib import Path


def entity_fields():
    return {
        "clients": ("client_id", "name", "contact_info"),
        "deliveries": (
            "delivery_id",
            "client_id",
            "expected_date",
            "actual_date",
            "status",
        ),
        "products": ("product_id", "name", "sku", "storage_conditions", "allowed_zone"),
        "delivery_items": (
            "delivery_item_id",
            "delivery_id",
            "product_id",
            "expected_quantity",
            "actual_quantity",
        ),
        "batches": (
            "batch_id",
            "delivery_item_id",
            "batch_number",
            "expiration_date",
            "quantity",
        ),
        "discrepancies": (
            "discrepancy_id",
            "delivery_item_id",
            "discrepancy_type",
            "quantity_difference",
            "description",
        ),
        "storage_cells": (
            "cell_id",
            "zone",
            "capacity",
            "available_capacity",
            "storage_conditions",
            "status",
        ),
        "placements": (
            "placement_id",
            "batch_id",
            "cell_id",
            "quantity",
            "placement_date",
        ),
    }


def require_quantity(value, minimum=0):
    if type(value) is not int or value < minimum:
        raise ValueError(f"Количество должно быть целым числом не меньше {minimum}.")
    return value


def require_date(value, optional=False):
    if optional and value is None:
        return value
    if not isinstance(value, str) or date.fromisoformat(value).isoformat() != value:
        raise ValueError("Дата должна иметь формат ГГГГ-ММ-ДД.")
    return value


def find_record(data, collection, key, value):
    for record in data[collection]:
        if record[key] == value:
            return record
    raise ValueError(f"Не найдена запись {collection}: {value}.")


def new_id(data, collection, key, prefix):
    used = {record[key] for record in data[collection]}
    number = len(used) + 1
    while f"{prefix}{number:03d}" in used:
        number += 1
    return f"{prefix}{number:03d}"


def validate_data(data):
    fields = entity_fields()
    if not isinstance(data, dict) or set(data) != set(fields):
        raise ValueError("JSON должен содержать восемь коллекций логической модели.")
    for collection, names in fields.items():
        if not isinstance(data[collection], list):
            raise ValueError(f"{collection}: требуется список.")
        ids = set()
        for record in data[collection]:
            if not isinstance(record, dict) or set(record) != set(names):
                raise ValueError(f"{collection}: поля не соответствуют ER-модели.")
            key = record[names[0]]
            if not isinstance(key, str) or not key or key in ids:
                raise ValueError(f"{collection}: пустой или повторный первичный ключ.")
            ids.add(key)
            for name in names:
                value = record[name]
                if (
                    name
                    in (
                        "actual_date",
                        "expiration_date",
                        "actual_quantity",
                        "allowed_zone",
                    )
                    and value is None
                ):
                    continue
                if name in (
                    "quantity",
                    "expected_quantity",
                    "actual_quantity",
                    "capacity",
                    "available_capacity",
                ):
                    require_quantity(
                        value, 1 if name in ("quantity", "capacity") else 0
                    )
                elif name == "quantity_difference":
                    if type(value) is not int:
                        raise ValueError("Разность количеств должна быть целой.")
                elif not isinstance(value, str) or (
                    not value.strip() and name != "description"
                ):
                    raise ValueError(f"{collection}.{name}: требуется непустая строка.")
                if name.endswith("_date"):
                    require_date(value)
    for collection in ("products", "storage_cells"):
        for record in data[collection]:
            if record["storage_conditions"] not in ("сухое", "охлаждение", "заморозка"):
                raise ValueError("Неизвестный код условий хранения.")
    links = (
        ("deliveries", "client_id", "clients", "client_id"),
        ("delivery_items", "delivery_id", "deliveries", "delivery_id"),
        ("delivery_items", "product_id", "products", "product_id"),
        ("batches", "delivery_item_id", "delivery_items", "delivery_item_id"),
        ("discrepancies", "delivery_item_id", "delivery_items", "delivery_item_id"),
        ("placements", "batch_id", "batches", "batch_id"),
        ("placements", "cell_id", "storage_cells", "cell_id"),
    )
    for child, fk, parent, pk in links:
        for record in data[child]:
            find_record(data, parent, pk, record[fk])
    pairs = set()
    for item in data["delivery_items"]:
        pair = (item["delivery_id"], item["product_id"])
        if pair in pairs:
            raise ValueError("Повторная пара (delivery_id, product_id).")
        pairs.add(pair)
        delivery = find_record(data, "deliveries", "delivery_id", item["delivery_id"])
        if (item["actual_quantity"] is None) != (delivery["actual_date"] is None):
            raise ValueError("Фактические дата и количество должны заполняться вместе.")
    for delivery in data["deliveries"]:
        if delivery["status"] not in (
            "ожидается", "поступила", "проверена", "требует согласования",
            "принята", "размещена",
        ):
            raise ValueError("Неизвестный статус поставки.")
        if (delivery["status"] == "ожидается") != (delivery["actual_date"] is None):
            raise ValueError("Статус поставки не согласован с датой поступления.")
        if not any(
            item["delivery_id"] == delivery["delivery_id"]
            for item in data["delivery_items"]
        ):
            raise ValueError("Поставка должна содержать позиции.")
    for batch in data["batches"]:
        item = find_record(
            data, "delivery_items", "delivery_item_id", batch["delivery_item_id"]
        )
        accepted = sum(
            b["quantity"]
            for b in data["batches"]
            if b["delivery_item_id"] == item["delivery_item_id"]
        )
        if not item["actual_quantity"] or accepted > item["actual_quantity"]:
            raise ValueError(
                "Принято больше фактически поступившего количества позиции."
            )
    for batch in data["batches"]:
        item = find_record(
            data, "delivery_items", "delivery_item_id", batch["delivery_item_id"]
        )
        placed = sum(
            p["quantity"]
            for p in data["placements"]
            if p["batch_id"] == batch["batch_id"]
        )
        if placed > batch["quantity"]:
            raise ValueError("Размещено больше принятого количества.")
    for cell in data["storage_cells"]:
        if cell["status"] not in ("доступна", "заблокирована"):
            raise ValueError("Неизвестный статус складской ячейки.")
        occupied = sum(
            p["quantity"] for p in data["placements"] if p["cell_id"] == cell["cell_id"]
        )
        if cell["available_capacity"] != cell["capacity"] - occupied:
            raise ValueError("Свободная вместимость не согласована с размещениями.")
    for placement in data["placements"]:
        batch = find_record(data, "batches", "batch_id", placement["batch_id"])
        item = find_record(
            data, "delivery_items", "delivery_item_id", batch["delivery_item_id"]
        )
        delivery = find_record(data, "deliveries", "delivery_id", item["delivery_id"])
        if delivery["actual_date"] is None or placement["placement_date"] < delivery["actual_date"]:
            raise ValueError("Размещение не может предшествовать поступлению.")
        product = find_record(data, "products", "product_id", item["product_id"])
        cell = find_record(data, "storage_cells", "cell_id", placement["cell_id"])
        if product["storage_conditions"] != cell["storage_conditions"]:
            raise ValueError("Условия хранения размещения не подходят товару.")
        if (
            product["allowed_zone"] is not None
            and product["allowed_zone"] != cell["zone"]
        ):
            raise ValueError("Зона размещения не подходит товару.")
    validate_delivery_states(data)
    validate_discrepancy_values(data)
    return data


def validate_delivery_states(data):
    """Партии и размещения должны соответствовать этапу обработки поставки."""
    for delivery in data["deliveries"]:
        item_ids = {
            item["delivery_item_id"] for item in data["delivery_items"]
            if item["delivery_id"] == delivery["delivery_id"]
        }
        batches = [b for b in data["batches"] if b["delivery_item_id"] in item_ids]
        deviations = [d for d in data["discrepancies"] if d["delivery_item_id"] in item_ids]
        accepted = delivery["status"] in ("принята", "размещена")
        if accepted != bool(batches):
            raise ValueError("Наличие принятых партий не согласовано со статусом поставки.")
        if delivery["status"] in ("ожидается", "поступила", "проверена") and deviations:
            raise ValueError("Расхождения не согласованы со статусом поставки.")
        if delivery["status"] == "требует согласования" and not deviations:
            raise ValueError("Для согласования должны быть зарегистрированы расхождения.")
        if delivery["status"] in ("проверена", "требует согласования", "принята", "размещена"):
            for item in data["delivery_items"]:
                if item["delivery_item_id"] not in item_ids:
                    continue
                difference = item["actual_quantity"] - item["expected_quantity"]
                quantitative = [
                    d for d in deviations
                    if d["delivery_item_id"] == item["delivery_item_id"]
                    and d["discrepancy_type"] != "качество"
                ]
                if bool(difference) != bool(quantitative) or len(quantitative) > 1:
                    raise ValueError("Количественные расхождения должны быть зарегистрированы полностью.")
                if delivery["status"] == "проверена" and difference:
                    raise ValueError("Проверенная поставка без расхождений не должна иметь отклонений количества.")
        if accepted:
            fully_placed = all(
                sum(p["quantity"] for p in data["placements"] if p["batch_id"] == b["batch_id"])
                == b["quantity"] for b in batches
            )
            if (delivery["status"] == "размещена") != fully_placed:
                raise ValueError("Статус поставки не согласован с размещением принятых партий.")


def validate_discrepancy_values(data):
    """Проверка вида, знака и величины отклонения по фактической позиции."""
    for deviation in data["discrepancies"]:
        item = find_record(
            data, "delivery_items", "delivery_item_id", deviation["delivery_item_id"]
        )
        actual = item["actual_quantity"]
        if actual is None:
            raise ValueError("Расхождение требует сохранённого фактического поступления.")
        kind = deviation["discrepancy_type"]
        if kind == "качество":
            if deviation["quantity_difference"] != 0 or not deviation["description"].strip():
                raise ValueError("Качественное отклонение требует описания и нулевой разности.")
        elif kind in ("недостача", "излишек", "неожиданный товар"):
            difference = actual - item["expected_quantity"]
            expected_kind = (
                "неожиданный товар" if item["expected_quantity"] == 0 and actual > 0
                else "недостача" if difference < 0
                else "излишек" if difference > 0 else None
            )
            if kind != expected_kind or deviation["quantity_difference"] != difference:
                raise ValueError("Вид и разность расхождения не соответствуют позиции поставки.")
        else:
            raise ValueError("Неизвестный тип расхождения.")


def load_data(path):
    with Path(path).open(encoding="utf-8") as stream:
        return validate_data(json.load(stream))


def save_data(data, path):
    validate_data(data)
    target = Path(path)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=target.parent, suffix=".tmp", delete=False
        ) as stream:
            temporary = Path(stream.name)
            json.dump(data, stream, ensure_ascii=False, indent=2, allow_nan=False)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
