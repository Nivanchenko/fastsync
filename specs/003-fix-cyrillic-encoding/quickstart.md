# Quickstart: проверка исправления кириллицы

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Предусловия

- JDK 25 и Maven 3.9.16 (см. `./scripts/install-maven.sh`), доступ к публичному p2 1С.
- Для ручной проверки — установленная 1C:EDT 2026.2.2 с русским интерфейсом.
- Для сценария консоли на Windows — Windows 10/11 с русской локалью (системная кодировка windows-1251).

## 1. Сборка и автоматические проверки

```bash
./scripts/build.sh
```

Ожидаемо: `scripts/check-encoding.sh` не находит не-ASCII в `.properties`, `BUILD SUCCESS`,
PDE-тесты зелёные, включая `LocalizationTest` и `ConsoleEncodingTest`.

Проверить отдельно:

```bash
./scripts/check-encoding.sh
```

Ожидаемо: пустой вывод, код возврата 0.

## 2. Метаданные каталога обновлений

```bash
python3 - <<'EOF'
import re, zipfile
z = zipfile.ZipFile('repositories/io.github.fastsync.edt.repository/target/repository/content.jar')
xml = z.read('content.xml').decode('utf-8')
for key in ('ru.featureName', 'ru.description', 'ru.pluginName'):
    for value in re.findall(r"property name='%s' value='([^']*)'" % key, xml):
        print(key, '=', value)
bad = re.findall(r"value='[^']*[\u00d0\u00d1\ufffd][^']*'", xml)
print('mojibake values:', bad)
EOF
```

Ожидаемо:

```text
ru.featureName = FastSync для 1C:EDT
ru.description = Плагин FastSync для 1C:EDT.
ru.pluginName = FastSync для 1C:EDT
mojibake values: []
```

## 3. Установка в EDT (US1, US3)

1. **Help → Install New Software → Add → Local…** → каталог `repositories/io.github.fastsync.edt.repository/target/repository/`.
2. В списке компонент: **FastSync для 1C:EDT**, описание «Плагин FastSync для 1C:EDT.».
3. Установить, перезапустить EDT, **Help → Installation Details**: название плагина по-русски.
4. Переключить язык EDT на английский (`-Duser.language=en` в `eclipse.ini` или настройка локали): названия английские, без изменений.
5. Контекстное меню проекта: пункт «Загрузить исходники в ИБ через ibcmd» читаем.

## 4. Консоль (US2)

- macOS/Linux: запустить загрузку на тестовой файловой ИБ; консоль FastSync — русские строки читаемы.
- Windows с русской локалью: то же; отдельно проверить строки с путями, содержащими кириллицу (создать проект/ИБ в каталоге с русским именем), и вывод предупреждений `ibcmd`.
- Ожидаемо: нет «Ð…», «Ñ…», «?» и ромбов вместо букв; заголовок консоли — `FastSync ibcmd`; пароль в строке команды заменён на `***`.

## 5. Регресс

- Полная и частичная загрузка, отмена, ошибка предупреждений `config apply` — поведение и состав отчёта те же.
- Английский интерфейс: `messages.properties`, `feature.properties`, `plugin.properties` не изменились (`git diff --stat`).
