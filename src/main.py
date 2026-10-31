"""Консоль приёмки товара на ответственное хранение"""

import json
from copy import deepcopy
from datetime import date
from pathlib import Path

from algorithms import select_storage_cell
from storage import find_record, load_data, new_id, save_data
from workflow import (
    accept_delivery,
    batch_remaining,
    delivery_items,
    record_actual_receipt,
    receipt_discrepancies,
    register_discrepancies,
    register_expected_delivery,
    register_placement,
)


def show(value):
    print(json.dumps(value, ensure_ascii=False, indent=2))


def ask(text):
    return input(text).strip()


def confirm(text):
    answer = ask(text + " [да/нет]: ").lower()
    if answer not in ("да", "нет"):
        raise ValueError("Введите «да» или «нет».")
    return answer == "да"


def read_items(quantity_key):
    print("Формат: P001:10,P002:5. Пустая строка — нет фактического товара.")
    text = ask("Позиции: ")
    items = []
    if text:
        for part in text.split(","):
            product_id, quantity = part.split(":")
            items.append(
                {"product_id": product_id.strip(), quantity_key: int(quantity.strip())}
            )
    return items


def read_date(text):
    value = ask(text + " (ГГГГ-ММ-ДД, пусто — сегодня): ")
    return value or date.today().isoformat()


def input_expected(data):
    show(data["clients"])
    client_id = ask("Клиент client_id: ")
    expected_date = read_date("Ожидаемая дата")
    show(data["products"])
    return register_expected_delivery(
        data,
        {
            "client_id": client_id,
            "expected_date": expected_date,
            "items": read_items("expected_quantity"),
        },
    )


def input_actual(data):
    delivery_id = ask("Поставка delivery_id: ")
    if not confirm("Ожидаемая поставка и сопроводительные документы найдены?"):
        print("Запросите уточнения у клиента. Фактическое поступление не сохранено.")
        return None
    actual_date = read_date("Дата поступления")
    items = read_items("actual_quantity")
    changed = record_actual_receipt(data, delivery_id, items, actual_date)
    result = receipt_discrepancies(data, delivery_id)
    print("Результат сопоставления (пустой список — расхождений нет):")
    show(result)
    return {
        "delivery_id": delivery_id,
        "updated_item_ids": changed,
        "discrepancies": result,
    }


def input_discrepancies(data):
    delivery_id = ask("Поставка delivery_id: ")
    deviations = receipt_discrepancies(data, delivery_id)
    items = delivery_items(data, delivery_id)
    item_ids = {item["delivery_item_id"] for item in items}
    for entry in data["discrepancies"]:
        if (
            entry["delivery_item_id"] in item_ids
            and entry["discrepancy_type"] == "качество"
        ):
            item = next(
                i for i in items if i["delivery_item_id"] == entry["delivery_item_id"]
            )
            deviations.append(
                {
                    "product_id": item["product_id"],
                    "discrepancy_type": "качество",
                    "quantity_difference": 0,
                    "description": entry["description"],
                }
            )
    while confirm("Добавить результат качественной проверки?"):
        deviations.append(
            {
                "product_id": ask("Товар product_id: "),
                "discrepancy_type": "качество",
                "quantity_difference": 0,
                "description": ask("Описание отклонения: "),
            }
        )
    result = register_discrepancies(data, delivery_id, deviations)
    show(result)
    if not confirm(
        "Подтверждены принимаемые количества? Расхождения согласованы с клиентом, критические отклонения урегулированы"
    ):
        print(
            "Расхождения сохранятся; партии не созданы. Продолжить можно через пункт 3."
        )
        return result
    details = {}
    for item in items:
        if item["actual_quantity"] > 0:
            print(
                f"Позиция {item['delivery_item_id']}, товар {item['product_id']}, факт {item['actual_quantity']}"
            )
            quantity = int(ask("Принятое количество (0 — не принимать позицию): "))
            if quantity == 0:
                continue
            details[item["delivery_item_id"]] = {
                "batch_number": ask("Номер партии: "),
                "expiration_date": ask("Срок годности ГГГГ-ММ-ДД (пусто — нет): ")
                or None,
                "quantity": quantity,
            }
    return accept_delivery(data, delivery_id, details, approved=True)


def choose_cell(data):
    show(data["batches"])
    batch_id = ask("Партия batch_id: ")
    batch = find_record(data, "batches", "batch_id", batch_id)
    quantity = batch_remaining(data, batch_id)
    if quantity <= 0:
        raise ValueError("Партия уже размещена.")
    item = find_record(
        data, "delivery_items", "delivery_item_id", batch["delivery_item_id"]
    )
    product = find_record(data, "products", "product_id", item["product_id"])
    zone = product["allowed_zone"]
    cell = select_storage_cell(product, quantity, data["storage_cells"])
    return batch_id, quantity, zone, cell


def input_placement(data):
    batch_id, quantity, zone, cell = choose_cell(data)
    if cell is None:
        print("Подходящей ячейки нет. Размещение не зарегистрировано.")
        return None
    show(cell)
    if not confirm("Товар физически размещён в выбранной ячейке?"):
        return None
    return register_placement(
        data,
        {
            "placement_id": new_id(data, "placements", "placement_id", "PL"),
            "batch_id": batch_id,
            "cell_id": cell["cell_id"],
            "quantity": quantity,
            "placement_date": read_date("Дата размещения"),
        },
        required_zone=zone,
    )


def main():
    path = Path(__file__).resolve().parent / "data" / "warehouse.json"
    try:
        data = load_data(path)
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"Не удалось загрузить данные: {error}")
        return 1
    menu = (
        "\n1. Зарегистрировать ожидаемую поставку\n"
        "2. Сопоставить фактическое поступление с ожидаемой поставкой\n"
        "3. Зарегистрировать расхождения\n4. Подобрать место хранения\n"
        "5. Зарегистрировать размещение товара\n6. Просмотреть данные\n0. Выход"
    )
    while True:
        try:
            print(menu)
            choice = ask("Выбор: ")
            if choice == "0":
                return 0
            if choice == "6":
                show(data)
            elif choice == "4":
                _, quantity, _, cell = choose_cell(data)
                (
                    show(cell)
                    if cell is not None
                    else print(f"Нет места для {quantity} единиц.")
                )
            elif choice in ("1", "2", "3", "5"):
                candidate = deepcopy(data)
                operations = {
                    "1": input_expected,
                    "2": input_actual,
                    "3": input_discrepancies,
                    "5": input_placement,
                }
                result = operations[choice](candidate)
                if result is not None:
                    if confirm("Сохранить изменения в JSON?"):
                        save_data(candidate, path)
                        data = candidate
                        print("Изменения сохранены.")
                        show(result)
                    else:
                        print("Изменения отменены.")
            else:
                print("Введите номер пункта от 0 до 6.")
        except (ValueError, KeyError, TypeError, OSError) as error:
            print(f"Ошибка ввода или сохранения: {error}. Повторите операцию.")
        except (EOFError, KeyboardInterrupt):
            print("\nРабота завершена. Неподтверждённые изменения не сохраняются.")
            return 0


if __name__ == "__main__":
    raise SystemExit(main())
