# Соответствие КТ2 и КТ3

| BPMN-операция | Python | Java | Данные |
|---|---|---|---|
| Зарегистрировать ожидаемую поставку | workflow.register_expected_delivery | ReceivingService.registerExpectedDelivery | Client, Delivery, DeliveryItem, Product |
| Сопоставить фактическое поступление с ожидаемой поставкой | algorithms.compare_delivery_items | DeliveryComparator.compareDeliveryItems | delivery_items: product_id, expected_quantity, actual_quantity |
| Зарегистрировать расхождения | workflow.register_discrepancies | ReceivingService.registerDiscrepancies | Discrepancy → DeliveryItem |
| Подобрать место хранения | algorithms.select_storage_cell | StorageSelector.selectStorageCell | Product.storage_conditions/allowed_zone; StorageCell.status/available_capacity/zone/storage_conditions |
| Зарегистрировать размещение товара | workflow.register_placement | ReceivingService.registerPlacement | Placement → Batch → DeliveryItem; StorageCell |

Каждая из Client, Delivery, Product, DeliveryItem, Batch, Discrepancy, StorageCell,
Placement имеет одноимённый Java-класс и UML-класс. JSON snake_case ↔ Java camelCase
через FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES. Все поля проверяются по
точному перечню IntegrityValidator.validateDocument; затем проверяются FK и
семантика IntegrityValidator.validate. Лишний Batch.product_id запрещён.
QualityDiscrepancy — специализация Discrepancy без новых JSON-полей и новой ER-сущности.

Batch.quantity — принятое количество, товар выводится через delivery_item_id.
available_capacity сверяется с capacity минус сумма размещений. Для недостачи и
излишка quantity_difference сверяется с actual_quantity минус expected_quantity.
Для качества — ноль и непустое описание. Проверка загрузки, pre/post операций,
перед сохранением; консоль работает с копией до успешной атомарной записи.

UML: девять редактируемых листов classes.drawio. 19 производственных типов:
18 классов и StorageRule. Поля и сигнатуры извлечены из скомпилированного кода
через javap -private; синтетические методы лямбд не показываются. Полные типы
стандартной библиотеки сокращены до простых имён. Конструкторы показаны.
Все поля закрыты. Софья Деревенец (ИКБО-10-24) подтвердила устранение всех
трёх замечаний к этому проекту; ссылки на подтверждения и соответствие SHA
приведены в ../review/review.md. Проверка её кода и UML выполнена на b810944;
ответы на два замечания к проекту партнёра ещё ожидаются.
