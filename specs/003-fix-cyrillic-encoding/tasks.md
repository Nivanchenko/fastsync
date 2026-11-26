# Tasks: корректное отображение кириллицы в установщике и консоли

**Input**: Design documents from `/specs/003-fix-cyrillic-encoding/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: включены — конституция проекта (принцип IV) и FR-006 требуют автоматическую проверку; в спеке тесты не отменялись.

**Organization**: задачи сгруппированы по user stories; MVP — US1 (названия в установщике), затем US2 (консоль), затем US3 (подписи/сообщения).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет зависимостей)
- **[Story]**: US1…US3
- Везде указаны точные пути файлов

## Path Conventions

Eclipse/Tycho multi-module (см. [plan.md](plan.md)):

- bundle: `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/`
- feature: `features/io.github.fastsync.edt.feature/`
- PDE tests: `tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/`
- сборка/CI: `scripts/`, `.github/workflows/`

---

## Phase 1: Setup (инфраструктура проверки)

**Purpose**: проверка «в `.properties` нет сырой кириллицы», общая для всех историй.

- [x] T001 Создать `scripts/check-encoding.sh`: `LC_ALL=C grep -n '[^[:print:][:space:]]'` по `find bundles features -name '*.properties' -not -path '*/target/*' -not -path '*/bin/*'`; при находках печатать файлы и выходить с кодом 1, иначе — код 0 и пустой вывод (переносимо: без `grep -P`, research R6).
- [x] T002 Подключить `scripts/check-encoding.sh`: вызов из `scripts/build.sh` перед `mvn` и отдельный шаг `Check localized resources are ASCII` в `.github/workflows/build.yml` (CI запускает `mvn verify` напрямую, не через `build.sh`).

---

## Phase 2: Foundational (блокирующие предпосылки)

**Purpose**: зависимости тестового бандла, без которых не запустится тест консоли.

- [x] T003 Добавить `org.eclipse.ui.console` в `Require-Bundle` файла `tests/io.github.fastsync.edt.tests/META-INF/MANIFEST.MF` (публичный бандл, уже требуется основным; нужен для типа `MessageConsole` в тесте).

---

## Phase 3: User Story 1 — русское название плагина в менеджере установки (P1)

**Goal**: метаданные p2 содержат читаемые русские `ru.featureName`, `ru.description`, `ru.pluginName`.

**Independent Test**: [quickstart.md](quickstart.md) §2 — разбор `content.jar` после сборки; опционально установка в EDT (§3).

- [x] T004 [P] [US1] Перевести `features/io.github.fastsync.edt.feature/feature_ru.properties` в ASCII с `\uXXXX`: `featureName=FastSync \u0434\u043b\u044f 1C:EDT`, `description=\u041f\u043b\u0430\u0433\u0438\u043d FastSync \u0434\u043b\u044f 1C:EDT.`, `providerName` без изменений; ключи и порядок строк сохранить.
- [x] T005 [P] [US1] Перевести `bundles/io.github.fastsync.edt/plugin_ru.properties` в ASCII с `\uXXXX`: `pluginName=FastSync \u0434\u043b\u044f 1C:EDT`, `upload.command.name=\u0417\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0438\u0441\u0445\u043e\u0434\u043d\u0438\u043a\u0438 \u0432 \u0418\u0411 \u0447\u0435\u0440\u0435\u0437 ibcmd`, `providerName` без изменений.
- [x] T006 [US1] Собрать `./scripts/build.sh` и проверить метаданные по [quickstart.md](quickstart.md) §2: `ru.featureName = FastSync для 1C:EDT`, `ru.description = Плагин FastSync для 1C:EDT.`, `ru.pluginName = FastSync для 1C:EDT`, `mojibake values: []`; зафиксировать вывод в этом файле (T006). Зависит от T004, T005.

---

## Phase 4: User Story 2 — кириллица в консоли на Windows (P1)

**Goal**: консоль декодирует UTF-8 независимо от кодировки рабочей среды.

**Independent Test**: [quickstart.md](quickstart.md) §4; автоматически — `ConsoleEncodingTest`.

- [x] T007 [US2] В `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/ui/ConsoleOutput.java` создать консоль с явной кодировкой: package-private `static MessageConsole newConsole()` → `new MessageConsole(Messages.get("console.name"), StandardCharsets.UTF_8.name(), null, true)`, конструктор использует этот метод. Состав строк, запись UTF-8-байтов и маскирование не менять.
- [x] T008 [US2] Добавить `tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/ConsoleEncodingTest.java`: загрузить `io.github.fastsync.edt.ui.ConsoleOutput` через `Platform.getBundle("io.github.fastsync.edt").loadClass(...)` (пакет `ui` не экспортируется), вызвать `newConsole()` рефлексией (`setAccessible(true)`) и проверить `getEncoding()` = `UTF-8` (research R5). Зависит от T007, T003.
- [ ] T009 [US2] **(не проверено: нет Windows-агента)** Ручная проверка консоли на Windows с русской локалью по [quickstart.md](quickstart.md) §4 (если Windows недоступен — записать как непроверенное с владельцем; локально проверить macOS).

---

## Phase 5: User Story 3 — подписи и сообщения плагина при любой кодировке (P2)

**Goal**: `messages_ru.properties` корректен для всех потребителей; подписи меню/консоли/диалогов/отчёта читаемы.

**Independent Test**: `LocalizationTest` + прогон сценария загрузки в EDT с русским UI ([quickstart.md](quickstart.md) §3, §5).

- [x] T010 [US3] Перевести `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/messages_ru.properties` (79 ключей) в ASCII с `\uXXXX`, сохранить ключи, комментарии, порядок строк и плейсхолдеры `{0}`, `{1}`.
- [x] T011 [US3] Добавить `tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/LocalizationTest.java`: читать `messages_ru.properties` (ресурс `/io/github/fastsync/edt/messages_ru.properties`) и `plugin_ru.properties` (ресурс `/plugin_ru.properties`) через `new Properties().load(InputStream)` (ISO-8859-1); проверить значения `mode.full` = `Полная загрузка`, `stage.import` = `Импорт конфигурации`, `pluginName` = `FastSync для 1C:EDT`, `upload.command.name` = `Загрузить исходники в ИБ через ibcmd`; во всех значениях обоих файлов не должно быть сигнатуры mojibake — символов U+00D0/U+00D1 (старшие байты UTF-8 кириллицы, прочитанные как ISO-8859-1) и U+FFFD; прочие Latin-1 символы (экранированные кавычки-ёлочки, неразрывный пробел) легитимны. Зависит от T010.
- [ ] T012 [US3] **(в EDT не проверено)** Убедиться, что существующий `MessagesTest` (русская локаль через `Messages.get`) проходит, и проверить в EDT с русским UI подписи меню, заголовок консоли, диалоги и отчёт ([quickstart.md](quickstart.md) §3, §5); зафиксировать результат.

---

## Phase 6: Polish (документация и финальная проверка)

- [x] T013 [P] Добавить в `AGENTS.md` (раздел «Разработка») правило: локализуемые `.properties` — только ASCII с `\uXXXX`; проверка `./scripts/check-encoding.sh` входит в `./scripts/build.sh` и CI.
- [x] T014 Финальный прогон `./scripts/build.sh`: `check-encoding.sh` проходит, `BUILD SUCCESS`, все PDE-тесты зелёные (включая `LocalizationTest`, `ConsoleEncodingTest`); `git diff --stat` не содержит `feature.properties`, `plugin.properties`, `messages.properties` (английская локаль не менялась) и не меняет логику маскирования; записать вывод в этом файле.
- [x] T015 Обновить отметки в [quickstart.md](quickstart.md), если команды/пути изменились по факту реализации; перечитать [contracts/localized-metadata.md](contracts/localized-metadata.md) и подтвердить соответствие собранных метаданных.

---

## Dependencies

```text
T001 → T002
T003 ────────────────┐
T004, T005 → T006    │
T007 → T008 ─────────┴→ T014
T010 → T011
T009, T012, T013, T015 — после соответствующих задач
```

- US1 (T004–T006) и US3 (T010–T012) независимы по файлам: `feature/plugin_ru` vs `messages_ru`.
- US2 (T007–T009) не зависит от US1/US3.
- Финальная проверка T014 — после всех задач реализации.

## Parallel Execution Examples

- US1: `T004` (feature_ru) и `T005` (plugin_ru) — разные файлы.
- US1/US3: `T004`, `T005`, `T010` можно готовить параллельно, затем сборка `T006` и тесты `T011`.
- US2 параллельно US1/US3, кроме общего финального прогона.

## Implementation Strategy

MVP: US1 (T004–T006) — пользователь видит корректное название при установке. Далее US2 (T007–T009) — читаемая консоль, затем US3 (T010–T012) — остальные подписи. Инкрементально: после каждого шага `./scripts/build.sh` остаётся зелёным.

## Implementation results (2026-11-26)

### T006 / T014 — сборка и метаданные p2

`./scripts/check-encoding.sh` перед сборкой: код 0, пустой вывод. Сборка (тот же набор целей, что в `scripts/build.sh`: `mvn -B -ntp clean verify -Dtycho.localArtifacts=ignore`; локальный Maven-репозиторий вынесен в `$TMPDIR`, см. примечание) — **BUILD SUCCESS**, 05:42 мин.

PDE-тесты: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`, `All tests passed`. Новые: `LocalizationTest` (`russianStringsSurviveIso88591Reading`, `russianResourcesContainNoMojibake`), `ConsoleEncodingTest` (`consoleIsCreatedWithUtf8Encoding`); существующие `MessagesTest`, `IbcmdRunnerTest` и остальные зелёные.

Метаданные p2 по [quickstart.md](quickstart.md) §2 (`repositories/io.github.fastsync.edt.repository/target/repository/content.jar → content.xml`):

```text
ru.featureName = FastSync для 1C:EDT
ru.description = Плагин FastSync для 1C:EDT.
ru.pluginName = FastSync для 1C:EDT
mojibake values: []
```

Дополнительно: `df_LT.featureName = FastSync for 1C:EDT`, `df_LT.pluginName = FastSync for 1C:EDT`, `org.eclipse.equinox.p2.bundle.localization = plugin` — английская локаль не изменилась. До фикса тот же разбор давал `ru.featureName = FastSync Ð´Ð»Ñ 1C:EDT` и 5 mojibake-значений.

`git diff --stat` не содержит `feature.properties`, `plugin.properties`, `messages.properties`; `IbcmdRunner` и маскирование секретов не менялись (единственная кодовая правка — `ConsoleOutput.java`).

Примечание: в песочнице сессии запись в `~/.m2` запрещена (`touch ~/.m2/... → Operation not permitted`), из-за чего Tycho падал с `lock timeout: Could not acquire lock on .../p2-artifacts.properties.tycholock` (две попытки). Сборка выполнена с `-Dmaven.repo.local=$TMPDIR/fastsync-m2/repository` (копия локального репозитория): состав целей и проверок идентичен `scripts/build.sh`.

### T009 / T012 — ручные проверки

- **T009 (Windows-консоль)**: не проверено — Windows-агента в CI нет, локально доступен только macOS. Косвенно: `ConsoleEncodingTest` подтверждает, что консоль создаётся с `UTF-8` (charset консоли и её `IOConsoleOutputStream`), т.е. байты записи и декодирования совпадают на любой ОС. Остаётся ручная проверка на Windows с русской локалью по [quickstart.md](quickstart.md) §4.
- **T012 (EDT с русским UI)**: `MessagesTest` (русская локаль через `Messages`) проходит в общем прогоне; `LocalizationTest` подтверждает корректное ISO-8859-1-чтение `messages_ru`/`plugin_ru`. Живая проверка подписей меню, заголовка консоли, диалогов и отчёта в EDT не выполнялась — пункты §3/§5 quickstart остаются ручными.
- **Проверка пользователем (2026-11-26)**: установка из локального update site (`Add → Local…` → `repositories/io.github.fastsync.edt.repository/target/repository`) — название и описание в установщике отображаются корректно (macOS). Консоль на Windows и подписи меню/диалогов в EDT не проверены.

### T015 — quickstart и контракт

Команды и пути в [quickstart.md](quickstart.md) не изменились (`./scripts/build.sh`, `./scripts/check-encoding.sh`, `content.jar`), правки не требуются. Значения из [contracts/localized-metadata.md](contracts/localized-metadata.md) подтверждены выводом выше.

### Уточнение детектора mojibake (после ревью)

- `LocalizationTest.assertNoMojibake` сужен до фактической сигнатуры порчи: `U+00D0`, `U+00D1` (старшие байты UTF-8 кириллицы, прочитанные как ISO-8859-1) и `U+FFFD`. Прочие Latin-1 символы (экранированные «», неразрывный пробел) легитимны, широкое правило «любой U+0080–U+00FF» давало бы ложные срабатывания. Формулировки в [research.md](research.md) (R4) и задаче T011 приведены к новому правилу.
- Пересборка тем же способом (см. примечание выше): **BUILD SUCCESS**, 05:34 мин, `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`; `LocalizationTest` (2 теста) и `ConsoleEncodingTest` (1 тест) зелёные.
- Метаданные p2 после пересборки: `ru.featureName = FastSync для 1C:EDT`, `ru.description = Плагин FastSync для 1C:EDT.`, `ru.pluginName = FastSync для 1C:EDT`, `mojibake values: []`.
- Формулировки в [contracts/localized-metadata.md](contracts/localized-metadata.md) и python-сниппете [quickstart.md](quickstart.md) §2 приведены к тому же правилу (U+00D0/U+00D1/U+FFFD), чтобы проверка не давала ложных срабатываний на легитимные Latin-1 символы.
- Проверка самого детектора на копиях ресурсов в `/tmp` (рабочее дерево не менялось): контрольная копия — `OK (2 tests)`; значение с `U+00D0` (`Ð´»Ñ`) — `Failures: 1`, сообщение `contains mojibake character U+00D0`; копия с легитимными `«»` и неразрывным пробелом — `OK (2 tests)`.
