# Коммит документации и объединение КТ3

Рабочий каталог — корень репозитория. Команды ниже предназначены для
Git Bash в терминале VS Code. Состояние описано на 24.11.2026.

## Текущие группы изменений

| Дата автора в текущей истории | SHA | Содержание |
|---|---|---|
| 22.11.2026 | 6ccd3a5 | Процедурные операции и документация КТ2 |
| 23.11.2026 | ca514c0 | Java 21, UML и эквивалентность |
| 24.11.2026 | 671fe85 / 32bb0dc / 16f8d23 | Три отдельных исправления по рецензии |
| 24.11.2026 | a9cf329 | Подтверждения Софьи и проверка её UML |

Это значения AuthorDate, читаемые `%ad`; CommitDate доступна отдельно:
`git log --format="%h %aI %cI %s"`. Полная история сохраняется командой Git;
резервная ветка входит в `--all`. Соответствие SHA находится в review/review.md.

## Зафиксировать документацию

```bash
git switch kt3-java-implementation
git status --short
git add -- README.md docs review/review.md src/java/README.md src/java/verification/requirements.md src/java/verification/verification.md
git diff --cached --stat
git diff --cached --check
git commit -m "Finalize KT3 documentation, current commit references, code metrics and peer-review status"
git push origin kt3-java-implementation
python docs/export_git_log.py
```

Коммит включает исправленный отчёт и документы docs/, соответствие SHA,
актуальные метрики и инструкцию выгрузки истории. Изменённый архив КТ2
и локальный RAR КТ3 не добавляются: не использовать `git add .`.
vcs/git_log.txt генерируется и остаётся исключённым из отслеживания.

## Объединить с main

Сначала сохранить оставшиеся локальные файлы (в текущей папке есть изменённый
архив КТ2 и неотслеживаемый RAR), затем выполнить обычное слияние:

```bash
git stash push --include-untracked -m "Preserve local archives while merging KT3"
git switch main
git pull --ff-only origin main
git merge --no-ff kt3-java-implementation -m "Merge verified KT3 Java implementation, peer-review corrections and documentation"
git push origin main
git stash pop
python docs/export_git_log.py
git status --short
git log -5 --oneline
```

Если любая Git-команда сообщает конфликт, остановиться и разобрать его до
следующей команды. Если stash не был создан, не выполнять stash pop.
Текущий план не требует force push. После успешного push main обновить
статус комплекта и историю в папке сдачи/архиве. Результат будущего слияния
не записывается заранее как выполненный.

## Выгрузка истории в UTF-8

Из корня проекта: `python docs/export_git_log.py`. Скрипт использует стандартную
библиотеку Python и сохраняет байты вывода Git напрямую, без перенаправления
PowerShell. Запускать после последнего коммита и после слияния. Содержимое
git_log.txt вручную не редактировать; файл по необходимости копируется в vcs/
комплекта сдачи. Тот же скрипт работает из Git Bash и PowerShell.
