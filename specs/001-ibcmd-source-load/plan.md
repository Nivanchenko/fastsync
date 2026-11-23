# Implementation Plan: загрузка исходников проекта EDT в файловую ИБ через ibcmd

**Branch**: `001-ibcmd-source-load` (текущая рабочая ветка `vendor/example`) | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-ibcmd-source-load/spec.md`

## Summary

Отдельная команда Eclipse в 1C:EDT 2026.2.2: пользователь выбирает проект с привязанной файловой ИБ, режим (полная/частичная загрузка), плагин проверяет цель (приложение по умолчанию, файловая ИБ, установка платформы и `ibcmd`), сохраняет проект, конвертирует исходники средствами EDT в XML во временный каталог, при необходимости закрывает сеанс агента Конфигуратора, выполняет `ibcmd infobase ... config import` (+ `config apply`, `--force` только после подтверждения) и обновляет состояние синхронизации EDT публичным API. Вся работа — через публичные API EDT, без создания ИБ, без принудительного завершения сеансов и без записи секретов в логи.

Технические решения и проверки среды — в [research.md](research.md); контракты — в [contracts/](contracts/); проверка — в [quickstart.md](quickstart.md).

## Technical Context

**Language/Version**: Java 25 (bytecode level 25), OSGi/Eclipse 4.38 (Eclipse 2025-12)

**Primary Dependencies**: 1C:EDT 2026.2.2 public bundles — `com.e1c.g5.dt.applications`, `com.e1c.g5.dt.applications.infobases`, `com._1c.g5.v8.dt.core`, `com._1c.g5.v8.dt.platform.services.core`, `com._1c.g5.v8.dt.platform.services.model`, `com._1c.g5.v8.dt.export`, `com._1c.g5.wiring`; Eclipse — `org.eclipse.ui`, `org.eclipse.ui.console`, JFace/SWT, `org.eclipse.core.runtime/resources`, `org.eclipse.core.jobs`; `ibcmd` платформы 1С 8.3.27; Maven + Tycho 5.0.2

**Storage**: внешняя существующая файловая ИБ (не создаётся); временные каталоги операции (`server-data`, `server-temp`, XML-выгрузка, ConfigDumpInfo) — удаляются всегда; модель проекта — в EDT

**Testing**: PDE/JUnit 4 plug-in tests в `tests/io.github.fastsync.edt.tests` + stub-скрипт `ibcmd` для процесса; ручной E2E в Eclipse Application по [quickstart.md](quickstart.md)

**Target Platform**: 1C:EDT 2026.2.2 (Java 25) на Eclipse SimRel 2025-12; macOS — основная среда, Windows/Linux учтены путями (`ibcmd`/`ibcmd.exe`, расположение `bin/`)

**Project Type**: многомодульный Eclipse/OSGi плагин (Tycho: bom, targets, bundles, features, repositories, tests)

**Performance Goals**: UI не блокируется; частичная загрузка не перевыгружает всю конфигурацию; длительность операции определяется `ibcmd`; preflight — секунды

**Constraints**: только публичные API EDT; без внутренних пакетов; без `infobase create`; без `--session-terminate=force` и принудительного завершения сеансов; никаких изменений ИБ до успешного preflight; секреты не логируются; строки локализуются; Java 25 / EDT 2026.2.2; сборка `./scripts/build.sh`

**Scale/Scope**: одна операция на проект/ИБ одновременно; конфигурации до тысяч объектов; временный диск — порядка размера XML-выгрузки

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` — незаполненный шаблон (не ратифицирован). Ворота выведены из `AGENTS.md` и обязательных ограничений `task.md`; при ратификации конституции их нужно туда перенести.

| Ворота | Статус | Обоснование |
|--------|--------|-------------|
| I. Только публичные API EDT, без внутренних пакетов | PASS | Используются публичные интерфейсы `com._1c.g5.v8.dt.*`/`com.e1c.g5.dt.*`; unchecked-исключение v2-flow не импортируется (research R4) |
| II. Целевая среда EDT 2026.2.2, Java 25, Eclipse 2025-12, Tycho 5.0.2 | PASS | Целевой target из официального примера 1С; сборка обновлена в `main`, переносится в рабочую ветку (R1) |
| III. Тест на добавленное поведение, без ослабления проверок | PASS | Планируются PDE/JUnit тесты: аргументы `ibcmd`, change set, предупреждения, маскирование, stub-процесс |
| IV. Пользовательские строки — в локализуемые `.properties` | PASS | `messages.properties` + `messages_ru.properties`, NLS `Messages` |
| V. Секреты не попадают в логи/вывод/отчёты | PASS | Маскирование аргументов и вывода; пароль не сохраняется (R7) |
| VI. Безопасность/сохранность данных: проверки до изменения ИБ, восстановление сеанса, честный отчёт | PASS | Preflight, `finally`-восстановление, state machine в [data-model.md](data-model.md) |
| VII. Минимальный объём: без расширений, CS/веб ИБ, создания ИБ, миграции данных | PASS | Явно вне объёма (spec Assumptions, research R10) |
| VIII. Сборка через `./scripts/build.sh`, Tycho/POM и документация синхронны | PASS | README и POM обновляются вместе с target |

**Post-design re-check (after Phase 1)**: PASS — дизайн не добавляет новых нарушений: внешних зависимостей/абстракций нет, все изменения остаются в одном bundle + тестах; единственная «нестандартность» — публичный v2-flow API с unchecked-исключением из internal-пакета (не импортируется, обосновано в research R4).

## Project Structure

### Documentation (this feature)

```text
specs/001-ibcmd-source-load/
├── plan.md                    # этот файл
├── research.md                # Phase 0: решения и проверки API/ibcmd
├── data-model.md              # Phase 1: сущности и state machine
├── quickstart.md              # Phase 1: проверка E2E вручную
├── contracts/
│   ├── command-contract.md    # UI-контракт команды
│   └── ibcmd-contract.md      # контракт вызовов ibcmd
├── checklists/requirements.md
└── spec.md
```

### Source Code (repository root)

В рабочей ветке кода нет — каркас собирается по официальному `1C-Company/dt-example-plugins` (master: Tycho 5.0.2, Java 25, Eclipse 2025-12, target `ruby/2026.2`) и адаптируется под `io.github.fastsync.edt`; каркас из `main` — вторичный референс. Ручная проверка — из Eclipse IDE для разработки плагинов 1C_EDT 2025-12: `/Users/nikita.ivanchenko/Library/Application Support/1C/1cedtstart/installations/Eclipse для разработки плагинов 1C_EDT 2025-12/eclipse.app`.

```text
pom.xml                         # корневой Tycho-проект
bom/pom.xml                     # Tycho 5.0.2, Java 25, Maven >= 3.9.9
targets/default/default.target  # EDT 2026.2 + Eclipse 2025-12 (официальный пример 1С)
targets/pom.xml, targets/default/pom.xml
bundles/io.github.fastsync.edt/
├── META-INF/MANIFEST.MF        # Require-Bundle: public EDT API + org.eclipse.ui.console
├── plugin.xml                  # команда/обработчик/меню
├── plugin.properties
├── build.properties, pom.xml
└── src/io/github/fastsync/edt/
    ├── Activator.java
    ├── Messages.java
    ├── messages.properties, messages_ru.properties
    ├── ui/UploadSourcesHandler.java      # AbstractHandler: selection, preflight, диалог, Job
    ├── ui/UploadModeDialog.java          # выбор FULL/PARTIAL + сведения о цели
    ├── ui/ConsoleOutput.java             # MessageConsole "FastSync ibcmd"
    ├── upload/UploadMode.java            # enum FULL/PARTIAL
    ├── upload/UploadJob.java             # Job, прогресс, отмена
    ├── upload/UploadOperation.java       # state machine, отчёт
    ├── upload/UploadReport.java
    ├── upload/UploadTarget.java
    ├── upload/TargetResolver.java        # приложение/ИБ/установка/ibcmd, preflight
    ├── upload/ChangeSet.java             # added/changed/removed, requiresFullReload
    ├── upload/SyncChangesCollector.java  # v2 flow: start/collect/finish/cancel
    ├── upload/SourceExporter.java        # EDT export → XML temp dir
    ├── upload/DesignerSessionGuard.java  # detect/close/open Designer Agent
    ├── upload/IbcmdCommandBuilder.java   # сборка аргументов, маскирование
    ├── upload/IbcmdRunner.java           # ProcessBuilder, streaming, cancel, exit code
    └── upload/SyncStateUpdater.java      # config export info + loadActualConfigDumpInfo

features/io.github.fastsync.edt.feature/     # feature.xml, pom.xml, feature.properties
repositories/io.github.fastsync.edt.repository/ # p2 update site
tests/io.github.fastsync.edt.tests/
├── META-INF/MANIFEST.MF       # Require-Bundle тестируемого bundle + JUnit
├── .classpath, build.properties, pom.xml
└── src/io/github/fastsync/edt/tests/
    ├── IbcmdCommandBuilderTest.java
    ├── ChangeSetTest.java
    ├── WarningsParserTest.java
    ├── IbcmdRunnerTest.java       # stub ibcmd script
    ├── TargetResolverTest.java    # моки EDT-сервисов
    └── RuntimeInstallationsTest.java
scripts/build.sh, scripts/install-maven.sh
.github/workflows/build.yml
README.md
```

**Structure Decision**: сохраняется структура репозитория из `main` (Tycho multi-module: bundle + feature + p2 repository + PDE tests) — она уже соответствует конвенциям `AGENTS.md`. Вся новая логика — в одном bundle `io.github.fastsync.edt`, разбитая по ответственности (preflight → change set → export → session guard → ibcmd → sync state → report), без дополнительных модулей и зависимостей.

## Complexity Tracking

> Нарушений Constitution Check нет; таблица не заполняется.

## Implementation outline (для `/speckit-tasks`)

1. **Каркас**: перенести из `main` обновлённую сборку (Tycho 5.0.2, Java 25, target EDT 2026.2 + Eclipse 2025-12), убедиться, что `./scripts/build.sh` зелёный до функциональных изменений.
2. **Preflight**: `TargetResolver` — приложение/ИБ/версия/установка/`ibcmd`, понятные ошибки, ни одного изменения ИБ.
3. **Диалог и Job**: команда, меню, выбор режима, прогресс/отмена, консоль вывода.
4. **Экспорт**: `IExportPreparationService` + `IExportOperationFactory` → temp XML; очистка temp.
5. **Change set**: v2-flow `start/collect`, решение full/partial, fallback на удаление/переименование, `cancel`/`finish`.
6. **Designer Agent**: detect/close/open, восстановление в `finally`, отказ при невозможности.
7. **ibcmd**: `IbcmdRunner` + `IbcmdCommandBuilder` (полная/частичная, `--partial`, confirm `--force`, маскирование, коды возврата, отмена).
8. **Sync state**: `config export info` → `loadActualConfigDumpInfo` → `finish`; при неуспехе — не обновлять.
9. **Отчёт/README/локализация** и тесты (unit + PDE + stub-процесс), ручной E2E по [quickstart.md](quickstart.md).
