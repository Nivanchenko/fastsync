# Research: загрузка исходников EDT в файловую ИБ через ibcmd

**Date**: 2026-11-13 | **Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Все решения проверены по установленной целевой среде: 1C:EDT 2026.2.2 (buildId 2026.2.2.21), платформенные bundle-ы `com._1c.g5.v8.dt.*` 2026.2.2 из `~/.p2/pool`, Java 25 (bundle-ы EDT объявляют `Bundle-RequiredExecutionEnvironment: JavaSE-25`, class-file major 69), Eclipse 2025-12 / SimRel 2025-12, `ibcmd` платформы 8.3.27.2342.

## R1. Базовая сборка: официальный пример 1С как источник каркаса

- **Decision**: каркас собран заново по официальному репозиторию `1C-Company/dt-example-plugins` (master, скачан 2026-11-13) и адаптирован под `io.github.fastsync.edt`: Tycho 5.0.2, `maven.compiler.release=25`, EE `JavaSE-25`, Maven prerequisites 3.9.9, target — официальный `targets/default/default.target` (EDT `ruby/2026.2`, Eclipse `releases/2025-12`, Orbit 4.38.0, ECF 3.15.7, юнит `com._1c.g5.v8.dt.product.application`). Ветка `main` содержит производный от того же примера каркас и используется как вторичный референс/для восстановления недостающих файлов.
- **Rationale**: master — актуальный источник сборки для EDT 2026.2: его `bom/pom.xml` уже содержит Tycho 5.0.2, Java 25 и `java.profile=JavaSE-25`, target совпадает с официальным. Адаптация namespace/структуры минимальна (один bundle вместо двух примеров, PDE-тесты вместо itests).
- **Alternatives**: пересоздать без оглядки на пример — риск расхождения с целевой платформой; копировать `main` целиком — в нём каркас старой ревизии примера.
- **Проверка**: версии и target сверены с master; `javac --release 25` против bundle-ов EDT 2026.2.2 проходит, unit-тесты проходят (см. отчёт реализации).

## R2. Целевое приложение, ИБ и версия платформы

Публичные API 2026.2.2 (проверены `javap`):

- `com.e1c.g5.dt.applications.IApplicationManager#getDefaultApplication(IProject)` → `IApplication`; `getRequiredVersion()`.
- `com.e1c.g5.dt.applications.infobases.IInfobaseApplication#getInfobase()` → `InfobaseReference`.
- `InfobaseReference#getInfobaseType()` (`FILE`/`SERVER`/`WEB`) и `getConnectionString()`; `FileConnectionString#getFile()`.
- `com._1c.g5.v8.dt.platform.services.core.runtimes.IRuntimeInstallationManager#getAllRegistered()`, `RuntimeInstallations.filterByVersion(String)`, `RuntimeInstallations.ENTERPRISE_PLATFORM`.
- `IRuntimeComponentManager#resolveExecutor(ILaunchableRuntimeComponent.class, IDesignerSessionThickClientLauncher.class, installation, IRuntimeComponentTypes.THICK_CLIENT)` → `ComponentExecutorInfo`.
- `com._1c.g5.wiring.ServiceAccess#get(Class)` — доступ к OSGi-сервисам EDT (использовался и в старом каркасе).

- **Decision**: брать только приложение по умолчанию проекта; отказывать, если его нет, это не `IInfobaseApplication`, тип ИБ не `FILE`, строка подключения не `FileConnectionString`, версия не задана или подходящая установка не одна. `ibcmd` ищется рядом с исполняемым файлом толстого клиента (см. R6), а не в `PATH`.
- **Rationale**: прямое соответствие FR-003/FR-004 из [spec.md](spec.md); `RuntimeInstallations.filterByVersion` уже проверен в старом каркасе.
- **Alternatives**: `IResolvableRuntimeInstallationManager#resolveByProjectAndInfobase` (используется внутри EDT) — сложнее и завязан на access type; для v1 достаточно фильтра по версии + `resolveExecutor`.

## R3. Конвертация исходников EDT → XML

Публичные API:

- `com._1c.g5.v8.dt.export.IExportOperationFactory#createExportOperation(Path, Version, IExportStrategy, EObject...)` → `IExportOperation#run(IProgressMonitor)`.
- `IExportStrategy.DEFAULT`; при необходимости — `IExportStrategy` с `exportSubordinatesObjects=true`.
- `IExportPreparationService#waitForExportPreparation(IDtProject, long, IProgressMonitor)`.
- Модель: `IV8ProjectManager#getProject(IProject)` → `IV8Project#getVersion()`; `IConfigurationProvider#getConfiguration(IProject)` → `Configuration` EObject; `IDtProjectManager#getDtProject(IProject)`.
- `com._1c.g5.v8.dt.export.OrdinaryDirectoryBuilder#create(Path)` — запись в каталог (публичный класс).

- **Decision**: `IExportPreparationService.waitForExportPreparation` → `IExportOperationFactory.createExportOperation(tempDir, projectVersion, strategy, configuration)` → `run(monitor)`. Экспорт пишет XML-дерево и `ConfigDumpInfo.xml` в `tempDir`; исходники проекта не трогаются. Для частичной загрузки экспортируются только изменённые объекты (см. R4).
- **Rationale**: ровно этот путь использует сам EDT внутри `AbstractInfobaseConnection#exportToXml` (проверено по байткоду 2026.2.2), т.е. формат гарантированно совместим с `ibcmd config import`.
- **Alternatives**: `IExportServiceRegistry#getExportService(version)` + `IExportService#work(EObject, IExportArtifactBuilder, ...)` + `OrdinaryDirectoryBuilder` — запасной вариант, если `IExportOperationFactory` не окажется доступен через `ServiceAccess` (кандидат на проверку первым делом в реализации).
- **Open item**: подтвердить, что `ServiceAccess.get(IExportOperationFactory.class)` возвращает сервис; иначе использовать fallback.

## R4. Набор несинхронизированных изменений и обновление состояния EDT

Публичный API `com._1c.g5.v8.dt.platform.services.core.infobases.sync.v2` (2026.2.2):

- `IInfobaseSynchronizationStateManager` (есть `SERVICE_NAME`): `startUpdateInfobaseFlow(IDtProject, InfobaseReference)` → `IUpdateInfobaseFlow`.
- `IUpdateInfobaseFlow` (расширяет `IInfobaseSynchronizationFlow`): `start()`, `finish()`, `cancel()`, `collectEdtObjectChanges()` → `Triple<Set<EObject>, Set<EObject>, Set<String>>` (добавленные, изменённые, удалённые URI), `isFullReloadRequired()`, `setFullReloadRequired(IDtProject)`, `loadActualConfigDumpInfo(Path)`, `hasSynchronizationInfo()`.
- `IWorkspaceOrchestrator#beginBackgroundOperation(String, Collection<IDtProject>, ProjectPipelineJob)` / `endOperation(Object)`; `ProjectPipelineJob.IMPORT` — публичный enum.
- `IInfobaseSynchronizationStateManager` — единственный способ получить change set без внутренних классов.

- **Decision**:
  1. Flow: `stateManager.startUpdateInfobaseFlow(dtProject, infobase)`; `flow.start()`.
  2. Change set собирается в `beginBackgroundOperation(..., ProjectPipelineJob.IMPORT)` (так делает и сам EDT) → `flow.collectEdtObjectChanges()`.
  3. `requiresFullReload = flow.isFullReloadRequired() || !removed.isEmpty() || userChoseFull`. Удалённые URI ловят и удаление, и переименование (переименование в XML меняет путь объекта: старая запись исчезает из ConfigDumpInfo) — это же трактует EDT в `checkPartialUpdateSupported`.
  4. После успешного `ibcmd`-импорта и `config apply`: `ibcmd ... config export info --out=<tmp>/dump` → `flow.loadActualConfigDumpInfo(<tmp>/dump/ConfigDumpInfo.xml)` → `flow.finish()`.
  5. При ошибке/отмене — `flow.cancel()`.
- **Rationale**: `finish()`/`cancel()` бросают unchecked `InfobaseSynchronizationException` из internal-пакета; байткод 2026.2.2 подтверждает, что тип наследует `RuntimeException`, поэтому его не нужно импортировать и достаточно `catch (RuntimeException)`. Внутренние классы не импортируются — используются только публичные интерфейсы.
- **Alternatives**: старый `IInfobaseSynchronizationManager#retrieveInfobaseChanges(..., IInfobaseChangesResolver, ...)` возвращает только статус, а не набор объектов; не подходит для частичной загрузки.
- **Open item**: поведение `loadActualConfigDumpInfo` + `finish` проверить E2E (без состояния синхронизации задача не принимается — FR-019).

## R5. Сеанс агента Конфигуратора (Designer Agent)

Публичный API 2026.2.2 (`com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IDesignerSessionThickClientLauncher`):

- `getDesignerSessionDirectory(component, infobase, args)` → `Path` (не `null` — подключение открыто).
- `closeDesignerSession(component, infobase, args)`.
- `openDesignerSession(component, infobase, IDesignerAgentConnectionListener, args)` → `Path`.
- `IDesignerAgentConnectionListener`: `closed()`, `reconnected(Path)`, `subscribed(Subscription)` — можно передать no-op реализацию.
- `RuntimeExecutionArguments` — пустой конструктор; для файловой ИБ без аутентификации достаточно пустых аргументов (старый диагностический код передавал `null`).

- **Decision**: перед операцией, если `getDesignerSessionDirectory(...) != null`, запомнить `wasConnected=true` и `closeDesignerSession(...)`. После операции (успех/ошибка/отмена) — если `wasConnected`, `openDesignerSession(..., no-op listener, ...)`; `finally` гарантирует восстановление. Процесс агента не завершается.
- **Rationale**: прямое соответствие FR-009…FR-011; публичный интерфейс.
- **Open item**: проверить, что `openDesignerSession` принимает `null`-listener или no-op; E2E на открытом сеансе агента.

## R6. Где `ibcmd` и как его вызывать (проверено вживую на 8.3.27.2342)

- **Decision (расположение)**: `ibcmd` лежит рядом с исполняемым файлом толстого клиента: macOS/Linux — `<install>/ibcmd`, Windows — `<install>/bin/ibcmd.exe`. Проверено: `/opt/1cv8/8.3.27.2342/{1cv8,ibcmd}`.
- **Decision (изоляция серверных данных)**: `ibcmd` требует каталог данных автономного сервера `--data`. Без него используется общий `~/.1cv8/1C/1cv8/standalone-server/`, и операция падает с «Ошибка блокировки каталога данных сервера» (проверено). Поэтому плагин передаёт приватные временные `--data` и `--temp`.
- **Decision (команды, проверены на временной файловой ИБ)**:
  - Полная загрузка: `ibcmd infobase --data=<tmp>/server-data --db-path=<IB> config import <xmlDir>` → `ibcmd ... config apply`.
  - Частичная: `ibcmd infobase ... config import files --partial --base-dir=<xmlDir> <relative files...>` → `config apply`.
  - Состояние: `ibcmd infobase ... config export info --out=<tmp>/dump` → `<tmp>/dump/ConfigDumpInfo.xml` (важно: `--out` — **каталог**, несмотря на формулировку в справке; проверено).
  - `infobase create` не используется (FR-006).
- **Воспроизведённые проверки** (временная ИБ `/tmp/ibcheck`, данные `/tmp/ibdata`):
  - `infobase --data=... --db-path=... create --create-database` → успех;
  - `config export <dir>` создал `ConfigDumpInfo.xml`, `Configuration.xml`, `Languages/`;
  - `config import <dir>` + `config apply` успешны, apply создал поколение конфигурации;
  - `config import files --partial --base-dir=<dir> Configuration.xml` → успех;
  - `config export info --out=<dir>` → `ConfigDumpInfo.xml`;
  - без `--data` — «Ошибка блокировки каталога данных сервера».
- **Open item**: эмпирически определить, как `config apply` сигнализирует о необходимости `--force` (код возврата/текст), и проверить повтор с `--force` после подтверждения. Пока правило: ненулевой код возврата + признаки предупреждений в выводе → спросить пользователя и повторить `config apply --force` ровно один раз.

## R7. Секреты и аутентификация

- `IInfobaseAccessManager#resolveSettings(InfobaseReference)` → `IInfobaseAccessSettings` (`userName()`, `password()`, `additionalProperties()`, `access()`).
- **Decision**: если настройки ИБ содержат пользователя/пароль, передавать их через `--user/--password` (или stdin-режим `-W`, если применимо) и **маскировать** значения в консоли, логах и отчётах; пароль никогда не сохраняется плагином. Для файловой ИБ без пользователей аргументы не нужны.
- **Residual risk**: CLI принимает пароль как аргумент процесса; другие процессы того же пользователя могут увидеть его в списке процессов. Зафиксировать в README/отчёте; по возможности использовать `--request-password`/`-W` (STDIN), если поддерживается в целевой версии.

## R8. UI и наблюдаемость

- Команда Eclipse (`org.eclipse.ui.commands`/`handlers`/`menus`) на выделенном проекте; старый каркас уже содержит рабочий пример (`plugin.xml`).
- Диалог выбора режима (full/partial) на SWT/JFace; после — `Job` с `setUser(true)`, отмена через `IProgressMonitor`.
- Вывод `ibcmd`: `org.eclipse.ui.console.MessageConsole` + `IOConsoleOutputStream`, команда и код возврата — в консоль; итог — `MessageDialog` + запись в Error Log.
- Строки — в `.properties` (`messages.properties`, `messages_ru.properties`).

## R9. Тестирование

- JUnit PDE plug-in tests (`tests/...`) для чистой логики: построение аргументов `ibcmd`, классификация change set (удаление/переименование → full), разбор предупреждений, маскирование секретов, очистка temp.
- `IbcmdRunner` тестируется на stub-скрипте (`ibcmd`-заглушка), который эмулирует вывод, коды возврата и долгое выполнение (для отмены).
- Ручной E2E — по [quickstart.md](quickstart.md): EDT 2026.2.2 + файловая ИБ 8.3.27.

## R10. Осознанно не делаем (v1)

- Расширения конфигурации, клиент-серверные/веб-ИБ, создание ИБ, миграция данных, принудительное завершение сеансов, автоматическое повышение версии формата выгрузки.
- Никаких новых зависимостей: JDK `ProcessBuilder`, SWT/JFace, Eclipse console.

## R11. Уточнения, полученные при реализации и E2E (EDT 2026.2.2, ibcmd 8.3.27)

- **Версия платформы**: `IApplication#getRequiredVersion()` часто пуст, поэтому используется цепочка приложение → `InfobaseReference#getVersion/getDefaultVersion` → `IV8Project#getVersion`. Если ED-API `IResolvableRuntimeInstallationManager#resolveByProjectAndInfobase(...)` доступен, установка берётся у EDT; иначе при нескольких билдах одной линейки выбирается новейший (8.3.27.2342).
- **Экспорт исходников**: `IExportOperationFactory` — Guice-биндинг внутри EDT, не OSGi-сервис; рабочий путь — публичная точка расширения `com._1c.g5.v8.dt.export.exportService` (`IExportService#work`) + `OrdinaryDirectoryBuilder`. `IExportServiceRegistry` через `ServiceAccess` тоже недоступен.
- **Состояние синхронизации**: `IInfobaseSynchronizationStateManager` доступен через `ServiceAccess`; `loadActualConfigDumpInfo(Path)` ожидает **каталог** (внутри ищется `ConfigDumpInfo.xml`) или zip-архив, но не сам XML-файл — иначе он трактуется как zip и падает.
- **Учётные данные**: `--user/--password` — параметры подкоманды `config ...`, а не `infobase`; при установке их до `config` ibcmd возвращает код 2 «Ошибка разбора параметра». Передаются в конце команды (`... config import <dir> --user=... --password=...`).
- **Generation id**: `ibcmd ... config generation-id` возвращает идентификатор поколения конфигурации ИБ; используется в `IUpdateInfobaseFlow#setActualGenerationId`, иначе статус приложения остаётся «требует загрузки» даже после успешного импорта и копирования ConfigDumpInfo.
- **`config apply` и подтверждения**: интерактивный запрос структуры БД (`[y/n]`) в полной загрузке даёт код 255 при закрытом stdin; в частичной загрузке после `import files --partial` тот же смысл может приходить кодом **102**. Плагин считает подтверждением наличия `[y/n]`/«Принять изменения»/«продолжить обновление», код 102, а также warning-маркеры. Политика `--force` привязана к настройке ИБ `IInfobasePreferencesManager#getPromptConfirmationOnRestructure(uuid)`: включена — первый `config apply` без `--force`, при запросе подтверждения спрашиваем и повторяем с `--force`; выключена — `config apply --force` сразу, без заведомо падающей попытки. Сеансы не завершаются, `--session-terminate=force` не используется.
- **Версия Java 25 и PDE**: self-hosted запуск EDT требует продукта `com._1c.g5.v8.dt.product.application.rcp` и полного набора `--add-opens`; без `--add-opens=java.base/java.nio=ALL-UNNAMED` падает 1C BM/Ignite. В установленной EDT 2026.2 команда проверена после ручной регистрации бандла в `bundles.info` (1cestart не обновил его после p2-установки).

## R12. Точечное обновление приложения

Представление приложений EDT — CommonNavigator с view id `com.e1c.g5.dt.applications.ui.view`; его popup-меню адресуется как `popup:com.e1c.g5.dt.applications.ui.view` (generic `popup:org.eclipse.ui.popup.any?objectClass=...` там не срабатывает). Команда добавлена в это меню и показывается для `IInfobaseApplication`; `UploadSourcesHandler` принимает выделенное приложение, `TargetResolver#resolve(IProject, IApplication, IMonitor)` обновляет ИБ именно этого приложения. Запуск из проекта остаётся и использует приложение по умолчанию, которое явно показывается в диалоге.

## R13. Ограничение fallback-экспорта для частичной загрузки

Публичный путь `com._1c.g5.v8.dt.export.exportService` (`IExportService#work`) при изменении только BSL-модуля не пишет XML-файлы объекта (в отличие от внутреннего `IExportOperation`, который использует штатная EDT). Поэтому если `exportPartial` вернул 0 файлов, плагин переходит на полную загрузку (сообщение «Частичный экспорт не дал файлов...»). Полная загрузка корректна, просто медленнее; отдельный shortcut задокументирован в `UploadOperation`.
