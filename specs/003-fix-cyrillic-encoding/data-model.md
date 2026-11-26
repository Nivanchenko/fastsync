# Data Model: корректное отображение кириллицы

**Date**: 2026-11-26 | **Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Постоянных данных фича не добавляет. Модель описывает ресурсы, их потребителей и проверки — чтобы «где читается» было зафиксировано явно.

## Ресурсы локализации

| Файл | Ключи | Значения (ru) | Потребитель при сборке/работе | Как читает | Требование |
|------|-------|---------------|-------------------------------|------------|------------|
| `features/io.github.fastsync.edt.feature/feature_ru.properties` | `featureName`, `providerName`, `description` | `FastSync для 1C:EDT`, `FastSync`, `Плагин FastSync для 1C:EDT.` | Tycho p2 publisher → `ru.featureName`, `ru.description`, `ru.providerName` в `content.xml`; менеджер установки EDT | `Properties.load(InputStream)` → ISO-8859-1 | только ASCII + `\uXXXX` |
| `bundles/io.github.fastsync.edt/plugin_ru.properties` | `pluginName`, `providerName`, `upload.command.name` | `FastSync для 1C:EDT`, `FastSync`, `Загрузить исходники в ИБ через ibcmd` | p2 publisher → `ru.pluginName`; OSGi bundle localization → `Bundle-Name`, `%upload.command.name` в меню | `Properties.load(InputStream)` → ISO-8859-1 | только ASCII + `\uXXXX` |
| `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/messages_ru.properties` | 79 ключей (`mode.*`, `stage.*`, `report.*`, `console.*`, `dialog.*`, `error.*`) | русские строки, локали `ru` | `io.github.fastsync.edt.Messages` → `ResourceBundle` (в Java 25 читает UTF-8) | `ResourceBundle.Control.FORMAT_PROPERTIES` | только ASCII + `\uXXXX` (иначе p2/ISO-8859-1-потребители ломаются) |

Английские файлы (`feature.properties`, `plugin.properties`, `messages.properties`) уже ASCII — не меняются (FR-007).

## Консоль операции

| Атрибут | Сейчас | Целевое |
|---------|--------|---------|
| Тип | `MessageConsole` (`true` — автоскролл) | без изменений |
| Кодировка (charset консоли и её output stream) | `WorkbenchEncoding.getWorkbenchDefaultEncoding()` (на русской Windows — windows-1251) | явно `UTF-8` |
| Байты, которые пишет плагин | UTF-8 (`ConsoleOutput.writeLine`) | UTF-8 (без изменений) |
| Источник строк | `Messages` (ru/en), строка команды, вывод `ibcmd` | без изменений |

Инвариант: кодировка записи (UTF-8) = кодировка декодирования консоли (UTF-8). Состав строк, порядок, маскирование секретов — не меняются.

## Матрица проверок

| Что проверяем | Как | Автоматически |
|---------------|-----|---------------|
| Нет сырой кириллицы ни в одном локализуемом `.properties` | `scripts/check-encoding.sh` (в `build.sh` и CI) | да |
| Значения бандл-ресурсов читаются корректно как ISO-8859-1, в них нет mojibake | `LocalizationTest` | да |
| Консоль создаётся с UTF-8 | `ConsoleEncodingTest` | да |
| Метаданные p2 содержат русские названия | разбор `content.jar` после сборки ([quickstart.md](quickstart.md)) | вручную/скриптом |
| Установка и название в менеджере установки, консоль на Windows | EDT с русским UI + Windows | вручную |

## Состояния

Нет — фича не вводит состояний и переходов.
