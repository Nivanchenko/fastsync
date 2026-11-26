# Research: корректное отображение кириллицы в установщике и консоли

**Date**: 2026-11-26 | **Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Проверки выполнены на текущем дереве: сборка `repositories/.../target/repository/` (собрана 2026-11-23), лог консоли коллеги `Log_FastSync.txt` (231 КБ), байткод `org.eclipse.ui.console` 3.15.0 из `~/.m2/repository/p2/osgi/bundle`.

## R1. Причина дефекта в каталоге обновлений: `.properties` читаются как ISO-8859-1

- **Факт**: в собранных метаданных `repositories/io.github.fastsync.edt.repository/target/repository/content.jar → content.xml` уже лежат битые значения:
  - `ru.featureName = 'FastSync Ð´Ð»Ñ 1C:EDT'`
  - `ru.description = 'ÐÐ»Ð°Ð³Ð¸Ð½ FastSync Ð´Ð»Ñ 1C:EDT.'`
  - `ru.pluginName = 'FastSync Ð´Ð»Ñ 1C:EDT'`
  Это ровно UTF-8-байты, прочитанные как ISO-8859-1: «Ð»/«Ñ»/«Ð´» — старшие байты UTF-8. В исходниках те же файлы лежат сырым UTF-8 (`file`: Unicode text, UTF-8).
- **Причина**: Tycho/p2 при публикации читает `feature_ru.properties`/`plugin_ru.properties` через `java.util.Properties.load(InputStream)`, который по контракту декодирует ISO-8859-1. Английские файлы ASCII, поэтому не страдают.
- **Decision**: хранить все локализуемые `.properties` в чистом ASCII, не-ASCII символы — в виде `\uXXXX` (формат native2ascii). `Properties.load` декодирует такие escape-последовательности при любой кодировке входа, поэтому один и тот же файл корректно читают p2/Tycho, OSGi bundle localization (`plugin_ru.properties` → `ru.pluginName`, `%upload.command.name`) и `ResourceBundle` в `Messages`.
- **Rationale**: минимальное изменение данных без новых механизмов; устраняет причину для всех потребителей сразу и на будущих сборках.
- **Alternatives**:
  - вписать русский текст прямо в `feature.xml` (XML объявлен UTF-8) — работает для p2, но теряется локализация en/ru и портится английский интерфейс (нарушает FR-007);
  - `-Dfile.encoding`/кодировка сборки — на `Properties.load` не влияет;
  - патчить Tycho — внешний инструмент, вне проекта.
- **Проверка**: тест `LocalizationTest` читает `messages_ru.properties` и `plugin_ru.properties` именно как ISO-8859-1 и проверяет значения; сборка проверяет ASCII во всех `.properties`; после сборки метаданные p2 проверяются в [quickstart.md](quickstart.md).

## R2. Причина дефекта в консоли: кодировка `MessageConsole` по умолчанию — кодировка рабочей среды

- **Факт**: `ConsoleOutput` пишет в поток UTF-8-байты (`ConsoleOutput.java:43`) и создаёт консоль двумя аргументами — `new MessageConsole(name, null)`. В целевой платформе (байткод `org.eclipse.ui.console` 3.15.0):
  - `MessageConsole(String, ImageDescriptor)` → `MessageConsole(String, ImageDescriptor, boolean)` → `IOConsole(String, String, ImageDescriptor, String encoding, boolean)`;
  - при `encoding == null` вызывается `WorkbenchEncoding.getWorkbenchDefaultEncoding()` и `Charset.forName(...)`; на русской Windows это windows-1251 → UTF-8-байты декодируются как однобайтовая кодировка;
  - `IOConsole.newOutputStream()` создаёт `IOConsoleOutputStream(this, this.charset)` — кодировка потока фиксируется в момент создания из charset консоли;
  - `IOConsole` не имеет `setEncoding`, есть только `getEncoding()/getCharset()` — кодировка задаётся только конструктором, поэтому менять её после создания консоли нельзя.
- **Факт из лога**: `Log_FastSync.txt` целиком восстанавливается обратным преобразованием windows-1251→UTF-8 («Запуск» → «Р—Р°РїСѓСЃРє», «Импорт» → «РРјРїРѕСЂС‚»), а 273 строки отличаются только символами U+FFFD на месте байтов, не определённых в cp1251 (0x98 и т.п.). Значит: (а) вывод `ibcmd` декодируется плагином правильно, порча — только на отображении; (б) дефект воспроизводится при системной кодировке, отличной от UTF-8, независимо от локали EDT.
- **Decision**: создавать консоль как `new MessageConsole(Messages.get("console.name"), StandardCharsets.UTF_8.name(), null, true)` — явная кодировка UTF-8 согласована с тем, что плагин пишет UTF-8.
- **Rationale**: детерминированно на любой ОС; не зависит от `WorkbenchEncoding` и настроек рабочей среды; совместимо с текущей записью байтов.
- **Alternatives**:
  - писать байты в кодировке консоли (`text.getBytes(console.getCharset())`) — на windows-1251 потеряются символы вне кодировки (длинное тире, стрелки, эмодзи), а при UTF-8-консоли сломается;
  - заставить пользователя менять кодировку консоли в настройках Eclipse — ручное действие, не исправление;
  - перекодировать вывод `ibcmd` — не требуется (см. выше), добавило бы эвристику без пользы.
- **Проверка**: тест `ConsoleEncodingTest` (см. R5); ручная проверка на Windows — в [quickstart.md](quickstart.md).

## R3. Кодировка вывода `ibcmd` не меняется

- **Decision**: оставить `InputStreamReader(..., UTF_8)` в `IbcmdRunner`.
- **Rationale**: лог коллеги показывает, что текст на входе консоли был корректным UTF-8; перекодирование вывода не требуется и может сломать macOS/Linux (FR-003/FR-005).
- **Alternatives**: определять кодовую страницу консоли Windows (`chcp`, `-Dfile.encoding`) — лишний процесс/настройка, противоречит фактам.

## R4. Регрессия для локализуемых ресурсов: тест + проверка сборки

- **Факт**: `Messages` использует `ResourceBundle.Control.FORMAT_PROPERTIES`; в Java 25 такой ресурс читается как UTF-8, поэтому сырая кириллица в `messages_ru.properties` сейчас работает в рантайме — но тот же файл p2 читает как ISO-8859-1. Тест «через `Messages`» этот класс дефекта не ловит, нужен тест именно на ISO-8859-1-чтение.
- **Decision**:
  1. `LocalizationTest` читает `messages_ru.properties` и `plugin_ru.properties` через `new Properties().load(InputStream)` (ISO-8859-1) по ресурсам бандла и проверяет значения ключей (`mode.full`, `pluginName`, `upload.command.name`, `console.name`), а также отсутствие в значениях сигнатуры порчи — символов U+00D0/U+00D1 (старшие байты UTF-8 кириллицы, прочитанные как ISO-8859-1) и U+FFFD (байт, не определённый в однобайтовой кодировке). Прочие Latin-1 символы (например, экранированные кавычки-ёлочки «» или неразрывный пробел) легитимны и допускаются — широкое правило «любой U+0080–U+00FF = mojibake» дало бы ложные срабатывания.
  2. `feature_ru.properties` не доступен из тестового бандла (фича не является зависимостью), поэтому все локализуемые `.properties` проверяются на ASCII в `scripts/check-encoding.sh`; скрипт вызывается из `scripts/build.sh` и отдельным шагом CI (CI запускает `mvn verify` напрямую, не через `build.sh`).
- **Rationale**: две проверки покрывают оба класса ошибок — «сырая кириллица» (ASCII-проверка, все файлы) и «неверное экранирование» (тест значений для бандла); лаконично, без новых зависимости.
- **Alternatives**: отдельный Maven-плагин/Enforcer — новая зависимость; сканирование дерева исходников из PDE-теста — ненадёжно (рабочий каталог Tycho-теста не корень репозитория).
- **Проверка**: `scripts/check-encoding.sh` запускается в `./scripts/build.sh` и CI; тест — в общем прогоне PDE-тестов.

## R5. Тест кодировки консоли без расширения API

- **Факт**: пакет `io.github.fastsync.edt.ui` не экспортируется (`Export-Package` — только `io.github.fastsync.edt` и `.upload`), поэтому тестовый бандл не может обратиться к `ConsoleOutput` напрямую; экспортировать UI-пакет ради теста — лишняя публичная поверхность.
- **Decision**: в `ConsoleOutput` выделить package-private `static MessageConsole newConsole()` (конструктор вызывает его), тест загружает класс через `Platform.getBundle("io.github.fastsync.edt").loadClass(...)`, вызывает метод рефлексией и проверяет `getEncoding()` = `UTF-8`.
- **Rationale**: проверяется фактическое создание консоли плагином; API не расширяется. Тестовому бандлу нужен `Require-Bundle: org.eclipse.ui.console` (публичный бандл, уже используется основным).
- **Risk/fallback**: `MessageConsole` в headless-тесте может потребовать UI-поток; CI запускает тесты под `xvfb-run`. Если создание консоли в тесте окажется нестабильным, тест заменяется проверкой кодировки через публичный `IOConsole` с тем же аргументом (значение `StandardCharsets.UTF_8.name()`), факт использования — код-ревью; решение фиксируется в tasks.md.
- **Alternatives**: экспортировать `ui` — отклонено; тест через `ConsoleOutput` (создаёт `ConsolePlugin` и регистрирует консоль) — тяжелее и требует UI-поток.

## R6. Проверка ASCII переносимым grep

- **Факт**: `grep -P` отсутствует в BSD grep на macOS (проверено: `grep: invalid option -- P`).
- **Decision**: `LC_ALL=C grep -n '[^[:print:][:space:]]'` по файлам `find bundles features -name '*.properties' -not -path '*/target/*' -not -path '*/bin/*'`; при находках — вывод файлов и exit 1.
- **Rationale**: POSIX-классы работают в BSD и GNU grep; в локали C `[[:print:]]` = ASCII 0x20–0x7E, поэтому любые не-ASCII байты (сырая кириллица) находятся, а `\uXXXX`-экранирование проходит.
- **Alternatives**: `python3`-скрипт — лишняя зависимость от наличия python в CI/локально (есть, но grep проще).

## R7. Документация правила

- **Decision**: одна строка в `AGENTS.md` (раздел «Разработка») — локализуемые `.properties` только ASCII с `\uXXXX`; проверка `scripts/check-encoding.sh` входит в `./scripts/build.sh` и CI.
- **Rationale**: `AGENTS.md` — актуальные указания разработчикам; README описывает сборку/установку, требования к кодировке файлов там не место.
