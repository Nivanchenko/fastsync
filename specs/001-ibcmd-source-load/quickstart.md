# Quickstart: проверка загрузки исходников в файловую ИБ через ibcmd

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

## Предварительные условия

- macOS (или Windows/Linux) с 1C:EDT **2026.2.2** (buildId 2026.2.2.21).
- Eclipse IDE для разработки плагинов **1C_EDT 2025-12** (Eclipse 2025-12): `/Users/nikita.ivanchenko/Library/Application Support/1C/1cedtstart/installations/Eclipse для разработки плагинов 1C_EDT 2025-12/eclipse.app`.
- JDK **25** (Axiom JDK из состава 1C или Zulu 25) и Maven 3.9.16.
- Установленная платформа 1С:Предприятие **8.3.27** с `ibcmd` (например, `/opt/1cv8/8.3.27.2342`).
- Существующая **файловая** ИБ (создаётся вне плагина) и проект EDT, привязанный к ней приложением.

## Сборка и тесты

```bash
./scripts/build.sh
```

Скрипт использует `JAVA_HOME` (по умолчанию Axiom JDK 25), запускает `clean verify`; результат p2 — `repositories/io.github.fastsync.edt.repository/target/repository/`.

PDE/JUnit тесты: проект `io.github.fastsync.edt.tests` → **Run As → JUnit Plug-in Test** (или в составе `verify`).

## Запуск в Eclipse PDE

1. Импортировать проекты репозитория: **File → Import → Existing Projects into Workspace**.
2. Открыть `targets/default/default.target`, авторизоваться в p2 1С, **Set as Active Target Platform**.
3. `io.github.fastsync.edt` → **Run As → Eclipse Application**.

## Положительный сценарий (полная загрузка)

1. В тестовой EDT открыть проект, привязанный к файловой ИБ (application → infobase).
2. Изменить один объект метаданных и сохранить.
3. ПКМ по нужному приложению (или по проекту, если приложение одно) → **Upload Sources to Infobase via ibcmd** → **Полная загрузка**.
4. Ожидаемо: диалог с целью (путь ИБ, версия платформы, `ibcmd`); прогресс; в консоли `FastSync ibcmd` — `config import` и `config apply` с кодом 0; итоговый диалог «успешно».
5. Проверить: конфигурация ИБ соответствует проекту; штатная загрузка EDT не предлагает повторно загрузить те же изменения; исходники проекта побайтово не изменены.

## Положительный сценарий (частичная загрузка)

1. Изменить один объект; ПКМ → команда → **Частичная загрузка**.
2. Ожидаемо: импорт только изменённых XML-файлов (`config import files --partial`), затем `config apply`; итог совпадает с полной загрузкой.
3. Удалить/переименовать объект и повторить частичную загрузку: ожидаемо — автоматический переход на полную загрузку с сообщением причины.

## Негативные проверки (ИБ не должна меняться)

- проект без привязанного приложения / приложение не ИБ;
- ИБ клиент-серверная/веб (не файловая);
- не найдена однозначная установка платформы;
- в установке отсутствует `ibcmd`;
- каталог ИБ недоступен.
Каждый случай: отказ до изменения ИБ + понятное сообщение. Проверить неизменность файлов ИБ (например, `shasum`/mtime) до и после.

## Сеанс агента Конфигуратора

1. Открыть сеанс агента для ИБ (запуск приложения из EDT).
2. Запустить загрузку: соединение должно быть закрыто перед обращением к ИБ и восстановлено после успеха.
3. Повторить с ошибкой/отменой: состояние соединения должно совпадать с исходным.

## Предупреждения apply и отмена

- Спровоцировать предупреждение при `config apply`; отказаться от `--force` → отчёт «импортировано, но не применено», синхронизация не обновлена; согласиться → повтор `config apply --force` один раз.
- Отмена на стадиях импорта/применения: процесс `ibcmd` прекращается, сеанс агента восстановлен, отчёт честно описывает состояние ИБ.

## Проверка секретов и вывода

- В консоли/логе нет паролей даже при их наличии в настройках ИБ; пароль не сохраняется плагином.
- Для каждой команды видны аргументы (с маскированием), вывод и код возврата.


## Диагностика: Run As → Eclipse Application и ошибка SWT (`UnsatisfiedLinkError`)

Симптом: `Could not load SWT library ... (have 'x86_64', need 'arm64e' or 'arm64')`.

Причина: Eclipse IDE для разработки плагинов 1C_EDT 2025-12 и target-платформа — **x86_64**, в bundle pool резолвится `org.eclipse.swt.cocoa.macosx.x86_64`. Если launch configuration берёт **arm64** JDK (например, Zulu 25 для Apple Silicon), SWT не загружается.

Решение:
1. **Preferences → Java → Installed JREs → Add → Standard VM** → указать x86_64 JDK 25:
   `/Applications/1C/1CE/components/axiom-jdk-full-25.0.2+12-x86_64`
2. **Run → Run Configurations… → Eclipse Application → JRE** → **Alternate JRE** → выбрать этот JDK (либо сделать его JRE по умолчанию в workspace).
3. Перезапустить конфигурацию.

Альтернатива — перевести target/запуск на `aarch64` (потребуются `org.eclipse.swt.cocoa.macosx.aarch64` и arm64 JDK); для текущей x86_64-сборки EDT это не требуется.


### Безвредные предупреждения Java 25 при запуске тестовой EDT

В логе Run As → Eclipse Application на Java 25 нормальны и не связаны с FastSync:
`Using incubator modules: jdk.incubator.vector`, `restricted method Runtime.load`, `SLF4J: Failed to load StaticLoggerBinder`, `Annotation classes from 'javax.inject'/'javax.annotation'`, `sun.misc.Unsafe::staticFieldBase` (Guice), `NLS unused message` (внутренние bundle-ы Eclipse/EDT), `CoreSVG has logged an error`, а также запись `1C:EDT закрывается` при закрытии приложения. Подавить часть из них можно VM-аргументами запуска: `--enable-native-access=ALL-UNNAMED`, `-Declipse.e4.inject.javax.warning=false`.


### PDE-запуск EDT: обязательны продукт EDT и VM-аргументы

Тестовая EDT должна запускаться **продуктом 1С**, а не `org.eclipse.platform.ide`: иначе 1C-сервисы не поднимаются — в логе `Service wait timed out`, `NullPointerException` в `EObjectProviderRegistry`, интерфейс зависает, проекты не создаются.

Готовый launch лежит в репозитории: `bundles/io.github.fastsync.edt/plugin.launch` (ПКМ → Run As → Eclipse Application). Он задаёт:
- `product = com._1c.g5.v8.dt.product.application.rcp`;
- JRE **JavaSE-25** — должно резолвиться в x86_64 JDK 25 (см. диагностику SWT выше);
- полный набор `--add-opens`/`--add-modules` и `-D`-аргументов из `1cedt.ini` EDT 2026.2.

Если вы уже запускали PDE-конфигурацию раньше, Eclipse сохранил её (например, `New_configuration.launch`) с `product=org.eclipse.platform.ide` — он не переиспользует настройки из `plugin.launch`. Поменяйте продукт в этой конфигурации или удалите её и запустите `plugin.launch` заново.

Если используется своя launch-конфигурация: на вкладке **Main** выберите **Run a product** → `com._1c.g5.v8.dt.product.application.rcp`, а VM arguments возьмите из `plugin.launch`. После смены продукта удалите старый runtime-workspace (`../runtime-New_configuration`) и создайте проект заново.


## Установка через p2-director (если Install New Software не регистрирует бандл)

В некоторых сборках EDT Lite (`1cestart`) после p2-установки `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info` не обновляется, и плагин не загружается. Надёжный способ — установить/обновить headless-director'ом при закрытой EDT:

```bash
ECLIPSE="/Users/nikita.ivanchenko/Library/Application Support/1C/1cedtstart/installations/Eclipse для разработки плагинов 1C_EDT 2025-12/eclipse.app/Contents/MacOS/eclipse"
"$ECLIPSE" -nosplash -consoleLog \
  -vm "/Applications/1C/1CE/components/axiom-jdk-full-25.0.2+12-x86_64/bin/java" \
  -application org.eclipse.equinox.p2.director \
  -repository "file:/Users/nikita.ivanchenko/Documents/repos/github/fastsync/repositories/io.github.fastsync.edt.repository/target/repository" \
  -installIU io.github.fastsync.edt.feature.feature.group \
  -destination "file:/Users/nikita.ivanchenko/Library/Application Support/1C/1cedtstart/installations/1C_EDT (Lite) 2026.2/1cedt.app/Contents/Eclipse"
```

Директор обновляет `bundles.info` сам. Перед запуском EDT должна быть закрыта.

## Ожидаемые артефакты после реализации

- `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/**` — команда, диалог, orchestrator, resolver, exporter, session guard, ibcmd runner, sync updater, report, messages.
- `tests/io.github.fastsync.edt.tests/src/**` — unit/PDE тесты (аргументы, change set, warnings, маскирование, stub `ibcmd`).
- Обновлённый `README.md` (требования, сборка, команда, ограничения).
