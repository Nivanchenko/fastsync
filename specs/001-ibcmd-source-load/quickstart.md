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
3. ПКМ по проекту → **Загрузить исходники в ИБ через ibcmd** → **Полная загрузка**.
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


### Обязательные VM-аргументы для PDE-запуска EDT (Java 25)

Без них 1C BM/Ignite не инициализируется (`InaccessibleObjectException: module java.base does not "opens java.nio"`), создание/импорт проекта падает, дерево метаданных показывается как структура каталогов.

**Run → Run Configurations… → Eclipse Application → Arguments → VM arguments** — добавить:

```text
-Dosgi.requiredJavaVersion=25
--add-modules=ALL-SYSTEM
--add-opens=java.base/java.lang.constant=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.ref=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.time=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
-Dorg.osgi.framework.bundle.parent=ext
-Dosgi.module.lock.timeout=240
-De1c.wiring.managedInitialization=true
-DmigrationEnabled=true
-Dlog4j.configuration=platform:/plugin/com._1c.g5.v8.dt.logging/log4j.properties
-Dlog4j.formatMsgNoLookups=true
-Dlog4j2.formatMsgNoLookups=true
```

Критичен `--add-opens=java.base/java.nio=ALL-UNNAMED`; остальные нужны остальным компонентам EDT. После падения первого запуска удалите из runtime-workspace частично созданный проект (`/Конфигурация`) с содержимым на диске и повторите.

## Ожидаемые артефакты после реализации

- `bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/**` — команда, диалог, orchestrator, resolver, exporter, session guard, ibcmd runner, sync updater, report, messages.
- `tests/io.github.fastsync.edt.tests/src/**` — unit/PDE тесты (аргументы, change set, warnings, маскирование, stub `ibcmd`).
- Обновлённый `README.md` (требования, сборка, команда, ограничения).
