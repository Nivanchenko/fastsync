# Contract: локализованные метаданные и кодировка консоли

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

## 1. Метаданные каталога обновлений (p2)

После сборки `repositories/io.github.fastsync.edt.repository/target/repository/content.jar → content.xml`
должны присутствовать свойства (значения — UTF-8 в XML, объявленном `encoding='UTF-8'`):

| Unit | Property | Value |
|------|----------|-------|
| `io.github.fastsync.edt.feature.feature.jar` | `df_LT.featureName` | `FastSync for 1C:EDT` |
| `io.github.fastsync.edt.feature.feature.jar` | `ru.featureName` | `FastSync для 1C:EDT` |
| `io.github.fastsync.edt.feature.feature.jar` | `ru.description` | `Плагин FastSync для 1C:EDT.` |
| `io.github.fastsync.edt` | `df_LT.pluginName` | `FastSync for 1C:EDT` |
| `io.github.fastsync.edt` | `ru.pluginName` | `FastSync для 1C:EDT` |
| `io.github.fastsync.edt` | `org.eclipse.equinox.p2.bundle.localization` | `plugin` |

Запрещено: значения, содержащие U+00D0/U+00D1 (UTF-8-байты кириллицы, прочитанные как ISO-8859-1) или U+FFFD. Прочие Latin-1 символы (экранированные кавычки-ёлочки, неразрывный пробел) легитимны.

## 2. Консоль FastSync

- Консоль создаётся с кодировкой `UTF-8` (charset консоли и её `IOConsoleOutputStream`).
- Плагин пишет в поток байты UTF-8 — кодировка записи и декодирования совпадают на любой ОС.
- Состав строк не меняется: строка команды (с маскированием секретов), вывод `ibcmd`, сообщения плагина, итог операции.
- Заголовок консоли — значение ключа `console.name` из `messages`/`messages_ru` (`FastSync ibcmd`, ASCII, не меняется).

## 3. Локализуемые `.properties`

- Файлы в кодировке ASCII; не-ASCII символы — `\uXXXX` (включая типографские: длинное тире, кавычки-ёлочки, знак номера).
- Ключи и их набор не меняются; английские файлы не меняются.
- Проверка: `scripts/check-encoding.sh` (ASCII) + `LocalizationTest` (значения при ISO-8859-1-чтении).
