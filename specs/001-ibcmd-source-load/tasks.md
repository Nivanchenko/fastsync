# Tasks: загрузка исходников проекта EDT в файловую ИБ через ibcmd

**Input**: Design documents from `/specs/001-ibcmd-source-load/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: включены принудительно — `AGENTS.md` требует тест для добавленного поведения; в спеке тесты не отменялись.

**Organization**: задачи сгруппированы по user stories из spec.md; MVP — US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: US1…US4 — принадлежность user story
- Везде указаны точные пути файлов

## Path Conventions

Рабочая ветка `vendor/example`, Eclipse/Tycho multi-module (см. plan.md):

- bundle: `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/`
- PDE tests: `tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/`
- test resources: `tests/io.github.fastsync.edt.tests/resources/`
- конфиги сборки: `pom.xml`, `bom/pom.xml`, `targets/`, `bundles/io.github.fastsync.edt/META-INF/MANIFEST.MF`, `bundles/io.github.fastsync.edt/plugin.xml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: восстановить каркас проекта и подготовить регистрацию команды.

- [x] T001 Собрать каркас по официальному `1C-Company/dt-example-plugins` (master) и адаптировать под `io.github.fastsync.edt` (Tycho 5.0.2, Java 25, каталоги bundle/feature/repository/tests); ветка `main` — вторичный референс для недостающих файлов; текущие `AGENTS.md` и `specs/` не затронуты.
- [x] T002 [P] Проверить `targets/default/default.target`: EDT `https://edt.1c.ru/downloads/releases/ruby/2026.2/`, Eclipse `releases/2025-12`, Orbit `4.38.0`, ECF `3.15.7`, юнит `com._1c.g5.v8.dt.product.application`; при расхождении — привести к официальному примеру 1С (research R1).
- [x] T003 Запустить базовую сборку `./scripts/build.sh`. Выполнено через Tycho 5.0.2 с p2-доступом (креды переданы транзитно, в репозиторий не сохранены): BUILD SUCCESS, 19/19 PDE-тестов.
- [x] T004 [P] Обновить `bundles/io.github.fastsync.edt/META-INF/MANIFEST.MF`: добавить `Require-Bundle`/`Import-Package` для `org.eclipse.ui.console`, `org.eclipse.core.jobs`, `com._1c.g5.v8.dt.core`, `com._1c.g5.v8.dt.export`; только публичные пакеты (без `com._1c.g5.v8.dt.internal.*`).
- [x] T005 [P] Обновить `bundles/io.github.fastsync.edt/plugin.xml`: убрать диагностическую команду, зарегистрировать `io.github.fastsync.edt.commands.uploadSources` (`org.eclipse.ui.commands`/`handlers`/`menus`, popup Project Explorer, ровно один `IProject`) по contracts/command-contract.md.
- [x] T006 [P] Удалить временную диагностику: `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/diagnostics/InspectProjectHandler.java` и её ключи из `plugin.properties`/`messages.properties`.
- [x] T007 [P] Создать NLS-каркас: `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/Messages.java`, `messages.properties` (базовый английский), `messages_ru.properties` (русский) с ключами команды, режимов, стадий, категорий ошибок.
- [x] T008 [P] Обновить `tests/io.github.fastsync.edt.tests/META-INF/MANIFEST.MF` и `build.properties`: зависимости `org.junit`, `org.mockito`, пакеты новых тестов, ресурсы stub-скрипта.

**Checkpoint**: каркас собирается, команда зарегистрирована, NLS готов.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: общие сущности и инфраструктура, без которых не начнётся ни одна user story.

**⚠️ CRITICAL**: до завершения этой фазы реализацию user stories не начинать.

- [x] T009 [P] Создать `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/upload/UploadMode.java` — enum `FULL`, `PARTIAL`.
- [x] T010 [P] Создать `upload/UploadTarget.java` — поля и валидационные правила из data-model.md (application, infobase, infobasePath, requiredVersion, installation, thickClient, ibcmd, configVersion, accessSettings).
- [x] T011 [P] Создать `upload/UploadReport.java` и `upload/UploadStage.java` — статусы стадий, режим (фактический), коды возврата `ibcmd`, итоговый текст для UI/лога (data-model.md, `UploadReport`).
- [x] T012 Реализовать `upload/TargetResolver.java`: `IApplicationManager#getDefaultApplication`, `IInfobaseApplication#getInfobase`, `InfobaseType.FILE` + `FileConnectionString#getFile`, `IApplication#getRequiredVersion`, `IRuntimeInstallationManager#getAllRegistered` + `RuntimeInstallations.filterByVersion`, `IRuntimeComponentManager#resolveExecutor(...THICK_CLIENT)`, путь `ibcmd` рядом с `1cv8` (`bin/ibcmd.exe` на Windows); на любой провал — типизированная ошибка, ИБ не трогается (research R2, R6).
- [x] T013 [P] Тесты `tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/TargetResolverTest.java` (Mockito): нет приложения; не ИБ; не файловая; нет версии; нет/несколько установок; нет `ibcmd` — все кейсы data-model.md `UploadTarget`.
- [x] T014 [P] Создать `upload/IbcmdCommandBuilder.java`: сборка команд полной/частичной загрузки, `config apply`, повтор с `--force`, `config export info --out=<dir>`, общие `--data`/`--temp`/`--db-path`, индексы секретных аргументов (contracts/ibcmd-contract.md).
- [x] T015 [P] Тесты `tests/.../IbcmdCommandBuilderTest.java`: порядок аргументов, абсолютные пути, `--partial`/`--base-dir`/список файлов, отсутствие `--session-terminate`, `--force` только в apply-повторе.
- [x] T016 [P] Создать `ui/ConsoleOutput.java`: `MessageConsole` «FastSync ibcmd», поток вывода, очистка/показ консоли (contracts/command-contract.md).
- [x] T017 Реализовать `upload/IbcmdRunner.java`: `ProcessBuilder`, объединённый stdout+stderr, потоковая передача в `ConsoleOutput`/колбэк, накопление вывода, код возврата, отмена через `IProgressMonitor` (уничтожение процесса), маскирование секретов (contracts/ibcmd-contract.md).
- [x] T018 [P] Создать stub-скрипт (успех, ненулевой код, «предупреждение», длительный процесс) и `tests/.../IbcmdRunnerTest.java`; stub создаётся тестом во временном каталоге, отдельный ресурс не нужен.
- [x] T019 Реализовать `upload/UploadJob.java`: Eclipse `Job` с `setUser(true)`, `SubMonitor` по стадиям, Cancel → отмена операции, обновление консоли/итогового отчёта.
- [x] T020 Прогнать `./scripts/build.sh`; убедиться, что фундаментальные тесты зелёные. BUILD SUCCESS (см. T003).

**Checkpoint**: foundation ready — можно начинать user stories.

---

## Phase 3: User Story 1 - Полная загрузка в привязанную файловую ИБ (Priority: P1) 🎯 MVP

**Goal**: пользователь запускает команду, выбирает полную загрузку, получает актуальную конфигурацию в ИБ и консистентное состояние синхронизации EDT.

**Independent Test**: проект с файловой ИБ, изменение одного объекта → команда → полная загрузка → `ibcmd config import` + `config apply` код 0, конфигурация ИБ обновилась, штатная загрузка EDT не предлагает те же изменения, исходники проекта не изменены.

### Tests for User Story 1

- [ ] T021 [P] [US1] `tests/.../SourceExporterTest.java` (Mockito) — полный экспорт: вызов `IExportPreparationService` + `IExportOperationFactory#createExportOperation(path, version, strategy, configuration)`, temp-каталог, `ConfigDumpInfo.xml`, удаление temp.
- [ ] T022 [P] [US1] `tests/.../UploadOperationFullTest.java` — happy path полной загрузки на фейках (resolver/exporter/runner/sync): порядок стадий, `config import` + `config apply`, обновление sync state, отчёт `DONE`.

### Implementation for User Story 1

- [x] T023 [US1] Реализовать `upload/SourceExporter.java` (полный экспорт): `IExportPreparationService#waitForExportPreparation`, `IExportOperationFactory`, `IExportStrategy`, `Version` из `IV8Project`; временный каталог `<tmp>/xml`; возвращает `ExportResult` (data-model.md).
- [x] T024 [US1] Реализовать `upload/SyncFlowSession.java`: обёртка публичного v2-flow — `IInfobaseSynchronizationStateManager#startUpdateInfobaseFlow(IDtProject, InfobaseReference)`, `start()`, `loadActualConfigDumpInfo(Path)`, `finish()`, `cancel()`; internal-типы не импортируются, ошибки — `RuntimeException` (research R4).
- [x] T025 [US1] Реализовать обновление состояния после импорта: `ibcmd config export info --out=<tmp>/dump`, `<tmp>/dump/ConfigDumpInfo.xml` → `SyncFlowSession#loadActualConfigDumpInfo` (встроено в `UploadOperation`, отдельный класс не понадобился); при неуспехе состояние не обновляется (FR-018/FR-019).
- [x] T026 [US1] Реализовать `upload/UploadOperation.java` — state machine `PRECHECK → SAVE → EXPORT → SESSION → IMPORT → APPLY → SYNC_STATE → DONE` для полной загрузки; `finally` — очистка temp и снятие блокировки «одна операция на проект» (data-model.md).
- [x] T027 [US1] Реализовать `ui/UploadModeDialog.java` (radio FULL/PARTIAL, сведения о цели) и `ui/UploadSourcesHandler.java` (selection → resolver → dialog → `UploadJob`); регистрация уже в T005.
- [x] T028 [US1] Подключить в `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/upload/UploadOperation.java` потоковый вывод `ibcmd` в `ui/ConsoleOutput.java` и прогресс по стадиям (через `IbcmdRunner`/`UploadJob`).
- [x] T029 [US1] E2E полной загрузки выполнен в установленной EDT 2026.2.2 на файловой ИБ 8.3.27.2342: `config import` → `config apply` (после подтверждения структуры/`--force`) → `config export info` → состояние синхронизации обновлено, итоговый успех.

**Checkpoint**: US1 работает и проверена независимо — MVP готов.

---

## Phase 4: User Story 2 - Частичная загрузка несинхронизированных изменений (Priority: P2)

**Goal**: перенос только изменённых объектов с автоматическим fallback на полную загрузку при удалении/переименовании.

**Independent Test**: изменение одного объекта → частичная загрузка → импорт только изменённых файлов, итог совпадает с полной; удаление/переименование → автоматически полная загрузка с сообщением.

### Tests for User Story 2

- [x] T030 [P] [US2] `tests/.../ChangeSetTest.java` — `removed` непустой → `requiresFullReload`; пустые added+changed → nothing; rename-сценарий (старый URI в removed).
- [ ] T031 [P] [US2] `tests/.../UploadOperationPartialTest.java` — частичная загрузка (файлы/`--partial`), fallback на полную при удалении, отчёт с причиной fallback.

### Implementation for User Story 2

- [x] T032 [US2] Создать `upload/ChangeSet.java` (added/changed/removed, `requiresFullReload`, `isEmpty`) по data-model.md.
- [x] T033 [US2] Расширить `upload/SyncFlowSession.java`: `collectChanges()` в `beginBackgroundOperation(..., ProjectPipelineJob.IMPORT)` → `flow.collectEdtObjectChanges()` → `ChangeSet` (left/middle/right) + `flow.isFullReloadRequired()` (research R4).
- [x] T034 [US2] Расширить `upload/SourceExporter.java`: частичный экспорт только изменённых объектов (added+changed), `IExportStrategy` с subordinates, список созданных XML-файлов для `config import files`.
- [x] T035 [US2] Расширить `upload/IbcmdCommandBuilder.java`: `config import files --partial --base-dir=<xmlDir> <relFiles...>` по contracts/ibcmd-contract.md (T014 частично готовит сборку — уточнить сигнатуру).
- [x] T036 [US2] Добавить в `upload/UploadOperation.java` ветку PARTIAL: сбор change set, `NOTHING_TO_UPLOAD`, fallback FULL при `requiresFullReload`, передача причины в `UploadReport`/UI.
- [x] T037 [US2] Дополнить `tests/.../IbcmdCommandBuilderTest.java` кейсами частичной загрузки (`--partial`, `--base-dir`, список файлов, пустой список не допускается).
- [x] T038 [US2] E2E частичной загрузки выполнен: `config import files --partial` → `config apply` (код 102 → подтверждение → `--force`) → sync, успех. Сценарий удаления/переименования с fallback на полную загрузку пока не проверялся.

**Checkpoint**: US1 и US2 работают независимо.

---

## Phase 5: User Story 3 - Безопасный отказ и сеанс агента Конфигуратора (Priority: P2)

**Goal**: при невозможности продолжить ИБ не меняется; открытый сеанс агента закрывается перед операцией и восстанавливается после любого исхода.

**Independent Test**: негативные preflight-кейсы не меняют ИБ (hash/mtime); при открытом сеансе агента он закрывается и восстанавливается после успеха, ошибки и отмены; при невозможности закрыть/восстановить — отказ до изменения ИБ.

### Tests for User Story 3

- [ ] T039 [P] [US3] `tests/.../DesignerSessionGuardTest.java` — нужен; guard реализован, но изолированный тест ещё не написан (проверяется E2E).
- [x] T040 [P] [US3] `tests/.../UploadSourcesHandlerTest.java` — негативный preflight: показывается локализованное сообщение, `UploadJob` не запускается.

### Implementation for User Story 3

- [x] T041 [US3] Реализовать `upload/DesignerSessionGuard.java`: `IDesignerSessionThickClientLauncher#getDesignerSessionDirectory/closeDesignerSession/openDesignerSession`, no-op `IDesignerAgentConnectionListener`, `wasConnected`/`restored` по data-model.md; процесс агента не завершается (research R5).
- [x] T042 [US3] Встроить `DesignerSessionGuard` в `upload/UploadOperation.java`: состояние `SESSION` после `EXPORT`, отказ до `IMPORT` при невозможности закрыть, восстановление в `finally` при `DONE`/`FAILED`/`CANCELLED`.
- [x] T043 [US3] Проверить, что все preflight-ошибки дают понятный текст из `messages.properties`/`messages_ru.properties`, и ни одна ветка не начинает импорт.
- [ ] T044 [US3] Preflight-негативы проверены в рантайме (нет версии/установки/сервисов — понятные сообщения). Восстановление сеанса агента после операции отдельно не подтверждено.

**Checkpoint**: US1–US3 работают независимо.

---

## Phase 6: User Story 4 - Подтверждение `--force`, отмена и наблюдаемость (Priority: P3)

**Goal**: предупреждения apply подтверждаются пользователем, отмена работает на всех стадиях, каждая команда и её код видны, секреты не утекают.

**Independent Test**: спровоцировать предупреждение apply → отказ от `--force` (импорт есть, apply нет, синхронизация не обновлена), согласие → один повтор с `--force`; отмена на разных стадиях; в консоли нет паролей.

### Tests for User Story 4

- [x] T045 [P] [US4] `tests/.../WarningsParserTest.java` — распознавание «нужно подтверждение/предупреждение» из вывода/кода, отсутствие `--session-terminate=force`.
- [x] T046 [P] [US4] `tests/.../SecretsMaskingTest.java` — пароль не попадает в вывод консоли, `UploadReport`, `toString()` команд и лог.
- [ ] T047 [P] [US4] `tests/.../UploadReportTest.java` — отчёт содержит режим, ИБ, все команды и коды, что применено/не применено.

### Implementation for User Story 4

- [x] T048 [US4] Реализовать `upload/WarningsParser.java`: анализ вывода/кода `config apply`, признак необходимости `--force`; правило — один повтор после подтверждения.
- [x] T049 [US4] Реализовать `ui/ConfirmForceDialog.java` и встроить в `upload/UploadOperation.java`: при отказе — «импортировано, но не применено», sync state не обновляется; при согласии — `config apply --force` один раз.
- [x] T050 [US4] Довести отмену: `IbcmdRunner` уничтожает процесс, `UploadOperation` проверяет `monitor.isCanceled()` между стадиями, состояние `CANCELLED`, восстановление сеанса, честный отчёт; дополнить `tests/.../IbcmdRunnerTest.java` (stub «долгий» процесс).
- [x] T051 [US4] Маскирование секретов в `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/upload/IbcmdCommandBuilder.java`, `upload/IbcmdRunner.java`, `ui/ConsoleOutput.java`, `upload/UploadReport.java` и записи в Error Log (FR-021).
- [x] T052 [US4] Итоговый отчёт/диалог: все стадии, команды, коды возврата, что импортировано/применено/осталось; вывод в консоль «FastSync ibcmd» (contracts/command-contract.md).
- [ ] T053 [US4] Подтверждение структуры/`--force` и вывод команд проверены в рантайме; отмена на разных стадиях и проверка секретов в живом запуске ещё не выполнялись.

**Checkpoint**: все четыре user stories работают независимо.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: документация, локализация, финальная проверка и коммит.

- [x] T054 [P] Обновить `README.md`: убрать описание временной диагностики, описать команду, требования (EDT 2026.2.2 / Java 25 / Eclipse 2025-12 / Tycho 5.0.2 / Maven 3.9.16), ограничения (только файловая ИБ, без создания ИБ, без завершения сеансов), риск видимости пароля как аргумента процесса.
- [x] T055 [P] Ревью локализации: все новые строки только в `messages.properties`/`messages_ru.properties`; паритет ключей ru/en; нет текста в коде.
- [x] T056 [P] Проверить отсутствие внутренних пакетов (`com._1c.g5.v8.dt.internal.*`) в импортах bundle и `Require-Bundle`; убрать мёртвый код/остатки диагностики.
- [x] T057 Запустить `./scripts/build.sh` (verify): все тесты зелёные. Итог: BUILD SUCCESS, 19/19 тестов, jar bundle содержит 24 класса и plugin.xml (исправлены `build.properties`, добавлен `org.eclipse.swt.svg` в extraRequirements, пакет `upload` экспортирован для тестов).
- [ ] T058 Пройти [quickstart.md](quickstart.md) целиком. Full и partial подтверждены; fallback при удалении/переименовании, отмена и восстановление сеанса агента остаются.
- [x] T059 Закоммитить реализацию в корне репозитория (`git add bundles/io.github.fastsync.edt tests/io.github.fastsync.edt.tests README.md` + `git commit`) с осмысленным сообщением.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей.
- **Foundational (Phase 2)**: после Setup — блокирует все user stories.
- **US1 (Phase 3)**: после Foundational; даёт ядро (exporter, sync flow, operation, UI).
- **US2 (Phase 4)**: после US1 (переиспользует `SourceExporter`/`SyncFlowSession`/`UploadOperation`).
- **US3 (Phase 5)**: после US1 (встраивается в `UploadOperation`); может выполняться параллельно с US2, если не трогать те же методы — на практике последовательно.
- **US4 (Phase 6)**: после US1 (runner/report); независим от US2/US3 по файлам, но интегрируется в `UploadOperation`.
- **Polish (Phase 7)**: после всех выбранных историй.

### User Story Dependencies

- **US1 (P1)**: MVP; ни от чего не зависит, кроме Foundational.
- **US2 (P2)**: зависит от ядра US1 (T023–T028).
- **US3 (P2)**: зависит от US1 (T026) — внедрение guard в operation.
- **US4 (P3)**: зависит от US1 (T017, T026) — warning/cancel/report.

### Within Each User Story

- Тесты пишутся до реализации и должны падать (по AGENTS.md).
- Модели → сервисы → UI/интеграция.
- После каждой истории — сборка + независимая проверка.

### Parallel Opportunities

- Setup: T002, T004, T005, T006, T007, T008 — разные файлы.
- Foundational: T009, T010, T011, T014, T015, T016, T018 — разные файлы; T017 после T014/T016.
- US1: T021 и T022 (тесты) параллельно; T023/T024/T025 — разные файлы, после них T026; T027/T028 после T026.
- US2: T030/T031 параллельно; T032 → T033; T034/T035 параллельно после T032.
- US3: T039/T040 параллельно; T041 → T042.
- US4: T045/T046/T047 параллельно; T048 → T049; T050/T051/T052 параллельно после T048.

---

## Parallel Example: User Story 1

```bash
# Тесты US1 параллельно:
Task: "SourceExporterTest в tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/SourceExporterTest.java"
Task: "UploadOperationFullTest в tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/UploadOperationFullTest.java"

# Реализация US1 параллельно (разные файлы):
Task: "SourceExporter в bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/upload/SourceExporter.java"
Task: "SyncFlowSession в bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/upload/SyncFlowSession.java"
Task: "SyncStateUpdater в bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/upload/SyncStateUpdater.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001–T008).
2. Phase 2: Foundational (T009–T020) — критично, блокирует всё.
3. Phase 3: US1 (T021–T029).
4. **STOP and VALIDATE**: E2E полной загрузки; исходники не изменены, состояние синхронизации консистентно.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → полная загрузка (MVP).
3. US2 → частичная загрузка + fallback.
4. US3 → безопасные отказы + сеанс агента.
5. US4 → `--force`/отмена/наблюдаемость.
6. Polish → README, локализация, полная сборка/E2E, коммит.

---

## Notes

- [P] — разные файлы, нет зависимостей.
- [Story] — трассировка к US1…US4 из spec.md.
- Тесты обязательны (AGENTS.md): писать до реализации, проверять падение.
- Коммит — после логических групп; финальный коммит — T059.
- Не добавлять зависимости/абстракции вне плана; внутренние пакеты EDT запрещены.
- Секреты, `--session-terminate=force`, `infobase create` — под запретом на всех этапах.

---

## Progress (2026-11-13)

**Сделано:** каркас сборки (Tycho 5.0.2 / Java 25 / EDT 2026.2 + Eclipse 2025-12), команда и меню, NLS ru/en, модели (`UploadTarget`, `UploadReport`, `ChangeSet`), `TargetResolver` с preflight, `IbcmdCommandBuilder`/`IbcmdRunner` (полная/частичная загрузка, `config apply`, `config export info`, маскирование пароля, отмена), `SourceExporter` (полный/частичный экспорт), `SyncFlowSession` (change set + state sync через публичный v2-flow), `UploadOperation` (state machine), `UploadJob`, диалог режима, консоль, README, тесты (`ChangeSetTest`, `IbcmdCommandBuilderTest`, `IbcmdRunnerTest`, `TargetResolverTest`) — 16/16 проходят локально, bundle компилируется `javac --release 25` против EDT 2026.2.2.

**Сборка:** `mvn verify` по Tycho 5.0.2 проходит: BUILD SUCCESS, 22/22 PDE-теста, p2-репозиторий собран.

**US3/US4:** реализованы `DesignerSessionGuard` (закрытие/восстановление соединения агента, отказ до импорта), `WarningsParser` + подтверждение `--force` (один повтор, без `--session-terminate=force`), отмена между стадиями, маскирование секретов в потоковом выводе, отчёт с командами/кодами.

**E2E (установленная EDT 2026.2.2 + файловая ИБ 8.3.27.2342):** полная и частичная загрузки прошли успешно; `config apply` с подтверждением структуры (`--force` по настройке ИБ) и обновление состояния синхронизации работают.

**Осталось:** тесты `UploadOperationFullTest`/`UploadOperationPartialTest`; E2E fallback при удалении/переименовании, отмена, восстановление сеанса агента (T044/T053/T058).

---

## Phase 8: Application-scoped command (follow-up)

- [x] T060 [US1] Добавить команду в контекстное меню приложения (`IApplication`): `UploadSourcesHandler` принимает выделенное приложение, `TargetResolver#resolve(IProject, IApplication, IMonitor)` обновляет именно его ИБ; в диалоге показывается имя приложения. Команда из проекта по-прежнему использует приложение по умолчанию. Подтверждено вживую: команда появилась в контекстном меню приложения и работает; сборка + unit-тест `TargetResolverTest#resolvesExplicitlySelectedApplication`.

- [x] T061 [US1] Локализация по языку интерфейса EDT (`Platform.getNL()`): `plugin_ru.properties`/`feature_ru.properties`, `Messages` без fallback на локаль JVM. Подтверждено вживую (русский UI — русские меню и сообщения), сборка + тест `MessagesTest`.

- [x] T062 [US1] После загрузки конфигурации дозагружать расширения проекта штатным механизмом EDT (`IV8ProjectManager` + `IInfobaseSynchronizationManager#updateInfobase`), итоговый диалог — таймлайн строк «время - действие - статус - длительность».
