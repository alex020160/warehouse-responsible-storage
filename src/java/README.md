# Приёмка товара на ответственное хранение — Java

ООП-реализация тех же операций и JSON-модели, что в процедурной версии `../`.
Рабочий каталог всех команд ниже: `src/java` (из корня репозитория: `cd src/java`).

## Среда и сборка

JDK 21, инструмент сборки — `javac --release 21`. Реально проверено на Windows:
Amazon Corretto 21.0.13.1.2, `java 21.0.13.1+2-LTS`, `javac 21.0.13.1`.
Единственная библиотека — Gson 2.13.2; скрипты загружают её с Maven Central
в `lib/` и проверяют SHA-256. При первой сборке нужен доступ в интернет.
JDK в архив не включён. Проверенный дистрибутив: [официальный Corretto 21.0.13.1.2 для Windows x64](https://corretto.aws/downloads/resources/21.0.13.1.2/amazon-corretto-21.0.13.1.2-windows-x64-jdk.zip); распакуйте ZIP и укажите каталог `jdk21.0.13_1` в JAVA_HOME. Установите JDK 21 и задайте его каталог в JAVA_HOME.

PowerShell (подставьте настоящий путь к установленному JDK):

```powershell
chcp 65001 | Out-Null
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk21.0.13_1'
& "$env:JAVA_HOME\bin\java.exe" -version
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Test
& "$env:JAVA_HOME\bin\java.exe" -cp 'build/classes;lib/gson-2.13.2.jar' ru.mirea.warehouse.cli.ConsoleApp
```

Linux/macOS (эти платформы в текущей среде не запускались):

```sh
export JAVA_HOME=/absolute/path/to/jdk-21
sh build.sh --test
"$JAVA_HOME/bin/java" -cp 'build/classes:lib/gson-2.13.2.jar' ru.mirea.warehouse.cli.ConsoleApp
```

Запускайте указанную Java из JAVA_HOME: системная Java может быть другой версии.
Можно передать путь к отдельному JSON последним аргументом ConsoleApp.
Данные изменяются только после подтверждения сохранения; для демонстрации используйте копию:

```powershell
Copy-Item -LiteralPath .\data\warehouse.json -Destination .\data\warehouse.backup.json
```

```sh
cp data/warehouse.json data/warehouse.backup.json
```

## Структура

- `src/main/java/ru/mirea/warehouse/domain/`: восемь сущностей, QualityDiscrepancy и Warehouse;
- `application/`: сопоставление, подбор, ReceivingService;
- `rules/`: StorageRule, ConditionsRule, ZoneRule;
- `infrastructure/`: проверка целостности и JSON;
- `cli/ConsoleApp.java`: консольное меню;
- `src/test/java/.../VerificationMain.java`: небольшой исполняемый стенд;
- `data/warehouse.json`: исходное состояние КТ2, восемь массивов сущностей;
- `data/demo_cases.json`: демонстрационные сценарии КТ2;
- `verification/`: одинаковые входы и фактические результаты двух версий;
- `build.ps1`, `build.sh`: воспроизводимая сборка и проверка.

## Операции и алгоритмы

| Python | Java |
|---|---|
| register_expected_delivery | ReceivingService.registerExpectedDelivery |
| compare_delivery_items | DeliveryComparator.compareDeliveryItems |
| register_discrepancies | ReceivingService.registerDiscrepancies |
| select_storage_cell | StorageSelector.selectStorageCell |
| register_placement | ReceivingService.registerPlacement |

Дополнительно перенесены recordActualReceipt, acceptDelivery, receiptDiscrepancies,
проверка целостности, чтение и атомарная запись JSON. Quantity и capacity — одинаковые
условные единицы; одна единица товара занимает единицу вместимости.

Сопоставление: два вложенных перебора, O(n*m+n+m), для n,m ≥ 1 — O(n*m).
Пустые списки обрабатываются. Рабочая память O(1), отдельный результат O(n+m).
Подбор: один проход, O(k), дополнительная память O(1), без сортировки.
Проверяются статус, режим хранения, allowed_zone и остаток вместимости.
При равенстве остатков сохраняется первая ячейка. Отсутствие результата — Optional.empty(),
при JSON-представлении проверок — null. Поиск внутри алгоритмов не заменён Map/Set.
Оценки считают арифметику количества элементарной операцией; BigInteger сохраняет
целочисленную семантику Python без переполнения long.

## Пример

Ожидание: `[{"product_id":"P001","expected_quantity":10}]`.
Факт: `[{"product_id":"P001","actual_quantity":8}]`.
Результат обеих версий:
`[{"product_id":"P001","discrepancy_type":"недостача","quantity_difference":-2}]`.

В меню для D001: 2 → D001 → да → 2026-11-22 → P001:10 → да;
3 → D001 → нет → да → 10 → DEMO-001 → пустой срок годности → да;
4 → B002: выбрана S001; 5 → B002 → да → 2026-11-22 → да.
Итог: PL002, available_capacity S001 = 0, статус D001 «размещена».
Повторное размещение отклоняется. Пункт 6 показывает JSON, 0 завершает работу.
Исходные демонстрационные данные не расходуются проверками.

## Проверки

`build.ps1 -Test` / `sh build.sh --test` собирает код и запускает базовую проверку.
Полный сохранённый набор парных проверок Java:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -cp 'build/classes;lib/gson-2.13.2.jar' ru.mirea.warehouse.VerificationMain verification/parity-input.json build/parity-java.json
```

```sh
"$JAVA_HOME/bin/java" -cp 'build/classes:lib/gson-2.13.2.jar' ru.mirea.warehouse.VerificationMain verification/parity-input.json build/parity-java.json
```

Сверка с Python выполняется из корня **репозитория**, где сохранена КТ2:
`py -3.13 docs/compare_versions.py . "$env:JAVA_HOME/bin/java.exe" work/parity`.
В ZIP КТ3 Python не повторяется. Сравниваются значения и порядок списков, null,
даты, типы расхождений и полное состояние; сообщения ошибок не обязаны совпадать дословно.
Описание фактических запусков находится в `verification/verification.md`.

Проверки исправлений по рецензии: `verification/review-fixes.md`.
Для проверки защиты сверки от неполного вывода выполните из корня репозитория:

```powershell
py -3.13 docs/verify_comparison_guard.py . "$env:JAVA_HOME/bin/java.exe"
```

```sh
python3 docs/verify_comparison_guard.py . "$JAVA_HOME/bin/java"
```

Стенд запускает настоящую Java, затем намеренно подменяет её выходной JSON:
полный массив принимается, укороченный, пустой, расширенный массив и объект
вместо массива отклоняются. Все временные данные создаются вне исходного JSON.


## Целостность и ООП

available_capacity = capacity − сумма Placement.quantity ячейки.
Для недостачи/излишка quantity_difference = actual_quantity − expected_quantity.
Для качества разность нулевая, обязательно описание; это отдельное поведение
QualityDiscrepancy.validateAgainst. StorageRule используется через два поля интерфейсного
типа; реализации ConditionsRule и ZoneRule проверяют разные ограничения.
Warehouse содержит List сущностей, Map используется для справочника товаров,
Set — для уникальности и обработки связанных идентификаторов.

Проверяется исходный JSON до десериализации, затем все ссылки, типы, даты, статусы,
количества, производные величины, допустимость размещений. Проверки выполняются при
загрузке, до и после операций изменения, перед сохранением. Консоль изменяет копию,
публикует её в памяти только после успешной записи временного файла и атомарной замены.
Ошибочный ввод и EOF не завершают программу с трассировкой исключения.
