# История и актуализация комплекта КТ3

КТ3 объединена с main коммитом 0cad37e. Последующие итоги взаимного
рецензирования фиксируются содержательным коммитом в main без повторного
слияния рабочей ветки. История и подтверждения сохраняются.

Из корня репозитория, Git Bash:

```bash
git status --short
git add -- review/review.md README.md docs/kt3_traceability.md docs/kt3_requirements.md docs/kt3_delivery_status.md docs/kt3_commit_plan.md docs/report_documentation_check.json docs/Отчет_ИКБО-10-24_ИвановаАВ_КТ3.docx src/java/verification/requirements.md src/java/verification/archive-check.json src/java/verification/archive-build.txt
git diff --cached --stat
git diff --cached --check
git commit -m "Confirm partner review corrections and finalize KT3 submission"
git push origin main
python docs/export_git_log.py
```

Эта последовательность применяется к настоящей редакции. После её выполнения
повторно запускать git commit без новых изменений не нужно. При изменениях
на удалённом main сначала согласовать их обычным слиянием; force push не нужен.
Пользовательские ZIP КТ2 и RAR КТ3 не включаются — git add . не применяется.

После последнего коммита журнал выгружается в UTF-8 без BOM и копируется
в новый комплект сдачи. В архив идут только требуемые памяткой файлы;
исходники Java сверяются с реально проверенной чистой распаковкой.
Старые SHA в цитатах рецензента сохраняются, соответствие текущим SHA
приведено в review/review.md. AuthorDate и CommitDate доступны раздельно:
`git log --format="%h %aI %cI %s"`. История — вывод Git, без ручного редактирования.
