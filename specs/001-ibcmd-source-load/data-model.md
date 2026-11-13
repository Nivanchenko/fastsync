# Data Model: загрузка исходников EDT в файловую ИБ через ibcmd

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Сущности

### UploadTarget

Разрешённая заранее цель операции. Создаётся только в preflight; при любой ошибке валидации не создаётся, и ИБ не изменяется.

| Поле | Тип | Правило |
|------|-----|---------|
| project | `IProject` | открытый проект EDT |
| dtProject | `IDtProject` | получен из проекта |
| application | `IApplication` | приложение по умолчанию; обязательно |
| infobase | `InfobaseReference` | из `IInfobaseApplication#getInfobase()` |
| infobasePath | `Path` | из `FileConnectionString#getFile()`; каталог должен существовать |
| requiredVersion | `String` | `IApplication#getRequiredVersion()`; не пустая |
| installation | `RuntimeInstallation` | ровно одна подходящая установка платформы |
| thickClient | `ILaunchableRuntimeComponent` | исполняемый файл толстого клиента |
| ibcmd | `Path` | `<каталог 1cv8>/ibcmd` (Windows — `bin/ibcmd.exe`); файл должен существовать и быть исполняемым |
| configVersion | `Version` | `IV8Project#getVersion()` — для экспорта |
| accessSettings | `IInfobaseAccessSettings` | из `IInfobaseAccessManager`; может быть `NOT_DEFINED` |

Проверки (все — до изменения ИБ): приложение есть; является приложением ИБ; `InfobaseType.FILE`; строка подключения `FileConnectionString`; каталог ИБ существует; версия задана; установка найдена однозначно; `ibcmd` существует. Провал любой проверки → `PRECHECK_FAILED`, ИБ не тронута.

### UploadMode

`FULL` | `PARTIAL` — явный выбор пользователя в диалоге; режим по умолчанию не применяется молча.

### ChangeSet

Только для `PARTIAL`; получен из EDT flow.

| Поле | Тип | Смысл |
|------|-----|-------|
| added | `Set<EObject>` | объекты, которых нет в ИБ |
| changed | `Set<EObject>` | изменённые объекты |
| removed | `Set<String>` | URI удалённых объектов (включая переименованные: старый путь исчезает) |

Правило: `requiresFullReload = !removed.isEmpty()`. Если `added + changed` пусто — загружать нечего (`NOTHING_TO_UPLOAD`, ИБ не трогаем). Если `requiresFullReload` — режим фактически `FULL`, пользователю показывается причина.

### ExportResult

| Поле | Тип | Смысл |
|------|-----|-------|
| directory | `Path` | временный каталог XML-выгрузки |
| configDumpInfo | `Path` | `<directory>/ConfigDumpInfo.xml` |
| files | `List<Path>` | XML-файлы, созданные экспортом (для `config import files`) |

### IbcmdInvocation

| Поле | Тип | Смысл |
|------|-----|-------|
| arguments | `List<String>` | полная командная строка без секретов в отображении |
| secretArguments | `Set<Integer>` | индексы аргументов, которые маскируются в выводе/логах |
| exitCode | `int` | код возврата процесса |
| output | `String` | stdout+stderr (с маскированием) |
| cancelled | `boolean` | процесс прерван пользователем |

Правило: успех — `exitCode == 0` и процесс не отменён; иначе — ошибка с выводом и кодом. Ни один секрет не попадает в `toString()`, консоль или лог.

### DesignerSessionState

| Поле | Тип | Смысл |
|------|-----|-------|
| wasConnected | `boolean` | был ли открыт сеанс агента до операции |
| closed | `boolean` | закрыли ли подключение мы |
| restored | `boolean` | восстановили ли подключение после операции |

Правила: если `wasConnected=false` — ничего не открываем; если `wasConnected=true` и закрыть не удалось — операция прерывается до изменения ИБ; восстановление выполняется при любом исходе; если восстановить не удалось — пользователю показывается явная ошибка (процесс агента не завершается).

### UploadOperation (state machine)

| Состояние | Что происходит | Переходы |
|-----------|----------------|----------|
| `PRECHECK` | разрешение цели и проверки | → `SAVE` / `FAILED` |
| `SAVE` | сохранение проекта, ожидание derived data | → `CHANGES` (partial) / `EXPORT` (full) / `FAILED` / `CANCELLED` |
| `CHANGES` | сбор change set, решение full/partial | → `EXPORT` / `NOTHING_TO_UPLOAD` |
| `EXPORT` | конвертация в temp XML | → `SESSION` / `FAILED` / `CANCELLED` |
| `SESSION` | закрытие сеанса агента (если открыт) | → `IMPORT` / `FAILED` |
| `IMPORT` | `ibcmd config import` (full/partial) | → `APPLY` / `FAILED` / `CANCELLED` |
| `APPLY` | `ibcmd config apply`, при предупреждениях — подтверждение и retry с `--force` | → `SYNC_STATE` / `FAILED` / `CANCELLED` |
| `SYNC_STATE` | `config export info` + `flow.loadActualConfigDumpInfo` + `flow.finish` | → `DONE` / `FAILED` |
| `DONE` | отчёт об успехе | — |
| `FAILED` | отчёт об ошибке + восстановление сеанса | — |
| `CANCELLED` | отчёт об отмене + восстановление сеанса + честное описание состояния ИБ | — |
| `NOTHING_TO_UPLOAD` | отчёт, ИБ не тронута | — |

Инварианты:
- ИБ изменяется только в состояниях `IMPORT`/`APPLY`; все проверки — раньше.
- `APPLY` не выполняется, если импорт не удался.
- `SYNC_STATE` не выполняется, если apply не применён; состояние синхронизации не обновляется при неуспехе.
- `finally`: удалить temp-каталоги, восстановить сеанс агента, снять блокировку «одна операция на проект».

### UploadReport

Итоговый отчёт: режим (фактический — full/partial/fallback), путь ИБ, версия платформы/`ibcmd`, стадии и их статусы, коды возврата всех команд, что импортировано/применено, что осталось несделанным, причина отмены/ошибки. Формируется всегда, включая отмену и ошибку.

## Связи

- `UploadTarget` — 1:1 с операцией; `ChangeSet` — 0:1 (только частичная загрузка); `ExportResult` — 1:1; `IbcmdInvocation` — 2..3 на операцию; `DesignerSessionState` — 1:1; `UploadReport` — 1:1.
- `UploadOperation` не переиспользуется между запусками; блокировка «одна операция на проект» — на уровне плагина (по `IProject`).
