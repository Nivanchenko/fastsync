# FastSync для 1C:EDT

Java-плагин для 1C:EDT: отдельная команда загрузки исходников проекта в привязанную **файловую** информационную базу через `ibcmd`.

## Установка

Update site: **https://nivanchenko.github.io/fastsync/repository/** (страница плагина — https://nivanchenko.github.io/fastsync/)

1. В 1C:EDT: **Справка → Установить новое ПО…** (в английском интерфейсе — **Help → Install New Software…**).
2. **Добавить… → Репозиторий** и в поле Location укажите `https://nivanchenko.github.io/fastsync/repository/`.
3. Выберите **FastSync for 1C:EDT** → **Далее** → примите условия → перезапустите EDT.

Нужна **1C:EDT 2026.2.2** (Java 25); в более старые EDT (2025.x) плагин не установится по BREE.

Офлайн-вариант: скачайте `fastsync-update-site.zip` из GitHub Release, распакуйте и укажите каталог через **Добавить… → Локальный…**.

> Если после установки команда не появилась: в некоторых сборках EDT Lite (`1cestart`) p2 не обновляет `configuration/.../bundles.info`, и бандл не загружается. Установите/обновите плагин headless p2-director'ом (команда — в [quickstart.md](specs/001-ibcmd-source-load/quickstart.md), раздел «Установка через p2-director»).

## Что делает плагин

Добавляет в EDT команду, которая загружает исходники проекта в привязанную к проекту **файловую** информационную базу напрямую через `ibcmd`: экспорт конфигурации средствами EDT в XML, затем `ibcmd infobase config import` + `config apply`. Штатную загрузку EDT команда не перехватывает и не заменяет — это альтернативный путь, который не требует запущенного клиента EDT-загрузки и работает на уровне файлов.

Загрузка бывает полной (**Full** — вся конфигурация) и частичной (**Partial** — только несинхронизированные изменения; переносятся метаданные, не данные). Если среди частичных изменений есть удалённые или переименованные объекты, плагин сам переходит на полную загрузку.

### Возможности

- проверки до изменения базы: приложение проекта, файловая ИБ, установка платформы, наличие `ibcmd`;
- закрытие и восстановление подключения агента Конфигуратора публичным API EDT (процесс агента не завершается);
- подтверждение `--force` при предупреждениях `config apply` (без `--session-terminate=force` и без завершения сеансов пользователей);
- обновление внутреннего состояния синхронизации EDT после успешной загрузки;
- прогресс, отмена, вывод команд `ibcmd` и коды завершения в консоли `FastSync ibcmd`; пароли маскируются и не логируются.

## Требования

- JDK 25 для Maven/Tycho (скрипт сборки берёт `JAVA_HOME`, по умолчанию — `/Applications/1C/1CE/components/axiom-jdk-full-25.0.2+12-x86_64`; в CI используется Azul Zulu JDK FX 25);
- Apache Maven 3.9.16 (установка: `./scripts/install-maven.sh`; Tycho 5.0.2 требует Maven 3.9.9+);
- 1C:EDT 2026.2.2 для запуска и ручной проверки; для разработки плагина используется Eclipse IDE для разработки плагинов 1C_EDT 2025-12 (Eclipse 2025-12):
  `/Users/nikita.ivanchenko/Library/Application Support/1C/1cedtstart/installations/Eclipse для разработки плагинов 1C_EDT 2025-12/eclipse.app`;
- установленная платформа 1С:Предприятие 8.3.27 (или другая поддерживаемая EDT 2026.2) с утилитой `ibcmd`;
- существующая файловая информационная база, привязанная к проекту приложением.

## Установка Maven

Из корня репозитория:

```bash
./scripts/install-maven.sh
```

Скрипт устанавливает Maven 3.9.16 в `~/.local/opt/apache-maven-3.9.16` и проверяет SHA-512 архива.

## Сборка и тесты

Из корня репозитория:

```bash
./scripts/build.sh
```

Скрипт использует `JAVA_HOME` (по умолчанию — Axiom JDK 25 из состава 1C), задаёт `MAVEN_HOME` и `PATH`, затем запускает `clean verify`. Если Maven ещё не установлен, сначала выполните `./scripts/install-maven.sh`.

p2-репозиторий 1С (`https://edt.1c.ru/downloads/releases/ruby/2026.2/`) публичный — учётные данные для сборки не нужны. Результат p2-сборки — `repositories/io.github.fastsync.edt.repository/target/repository/`.

PDE/JUnit тесты: проект `io.github.fastsync.edt.tests` → **Run As → JUnit Plug-in Test** (или в составе `verify`).

## Установка локально собранного плагина

Для проверки изменений ставьте плагин в установленную **1C:EDT 2026.2.2**: self-hosted PDE-запуск EDT на Java 25 нестабилен (`PlatformPlugin.getRuntimeRegistry() is null`, таймауты сервисов).

1. Соберите p2-репозиторий: `./scripts/build.sh` → `repositories/io.github.fastsync.edt.repository/target/repository/`.
2. В EDT: **Help → Install New Software… → Add… → Local…** → укажите каталог `repositories/io.github.fastsync.edt.repository/target/repository`.
3. Выберите **FastSync for 1C:EDT** → Next → примите условия → Restart.
4. Откройте проект с файловой ИБ и выполните ПКМ → **Upload Sources to Infobase via ibcmd**.

После изменения кода: пересоберите p2 и повторите установку/обновление (**Help → Installation Details → Update**).

## Запуск в Eclipse PDE

1. Импортируйте проекты из репозитория: **File → Import → Existing Projects into Workspace**.
2. Откройте `targets/default/default.target` в Target Editor и выберите **Set as Active Target Platform**. p2-репозиторий 1С публичный; если Target Editor всё же запросит авторизацию, войдите своей учётной записью 1С, но не сохраняйте учётные данные в репозитории.
3. Запустите **Run As → Eclipse Application** для проекта `io.github.fastsync.edt`.

> Запускайте готовым `bundles/io.github.fastsync.edt/plugin.launch` (ПКМ → Run As → Eclipse Application): он задаёт EDT-продукт `com._1c.g5.v8.dt.product.application.rcp`, x86_64 JDK 25 и обязательные `--add-opens` для Java 25. С `org.eclipse.platform.ide` 1C-сервисы не поднимаются (зависание, `Service wait timed out`), а без `--add-opens=java.base/java.nio=ALL-UNNAMED` падает 1C BM/Ignite.

> Если при запуске возникает `UnsatisfiedLinkError` про SWT (`have 'x86_64', need 'arm64'`): IDE и target-платформа — x86_64, а launch configuration взяла arm64 JDK. Добавьте в **Preferences → Java → Installed JREs** x86_64 JDK 25 (`/Applications/1C/1CE/components/axiom-jdk-full-25.0.2+12-x86_64`) и выберите его в **Run Configurations → JRE → Alternate JRE**.

## Использование

1. В EDT откройте проект, привязанный к файловой ИБ (приложение проекта содержит ИБ и требуемую версию платформы).
2. Обновить конкретную ИБ: ПКМ по нужному приложению в представлении приложений → **Upload Sources to Infobase via ibcmd** (диалог покажет приложение и его ИБ). Если приложений несколько, запуск из проекта использует приложение по умолчанию — оно явно видно в диалоге.
3. Выберите **Full** или **Partial**; в диалоге показаны проект, путь ИБ, версия платформы и путь `ibcmd`.
4. Наблюдайте прогресс; команды `ibcmd`, их вывод и коды завершения — в консоли **FastSync ibcmd**. Операцию можно отменить.
5. Итоговый диалог сообщает, что импортировано и применено, и обновлено ли состояние синхронизации EDT.

Поведение при отказах: при отсутствии приложения/файловой ИБ/установки платформы/`ibcmd` база не изменяется и пользователь получает сообщение с причиной; `--force` запрашивается только при предупреждениях `config apply`; сеансы пользователей не завершаются.

## Язык интерфейса

Подписи меню, диалогов и сообщений плагина следуют языку интерфейса EDT: русский `NL` — русские строки (`plugin_ru.properties`, `messages_ru.properties`), любой другой — английские. Название feature в списке устанавливаемого ПО также локализовано.

## Ограничения

- только существующая файловая ИБ; клиент-серверные и веб-базы, создание ИБ и миграция данных вне объёма;
- частичная загрузка переносит только изменения конфигурации (метаданных), не данные;
- `ibcmd` принимает пароль как аргумент процесса — при наличии учётных данных пароль может быть кратковременно виден другим процессам того же пользователя; плагин маскирует его в выводе и не сохраняет;
- расширения конфигурации не поддерживаются в v1.

## Публикация update site

`.github/workflows/build.yml` собирает p2-репозиторий на GitHub Actions при push в `master`, при push тега `v*` и вручную (`workflow_dispatch`):

- сборка и тесты — `mvn clean verify` (Tycho) на JDK 25 (Azul Zulu FX) в Xvfb; p2-репозиторий 1С публичный, secrets не нужны;
- p2-репозиторий публикуется на GitHub Pages: `https://nivanchenko.github.io/fastsync/repository/`;
- артефакт `p2-repository` доступен на странице запуска workflow;
- на теге `v*` создаётся GitHub Release с `fastsync-update-site.zip` — архивом p2-репозитория для офлайн-установки.

Однократная настройка репозитория: **Settings → Pages → Build and deployment → Source: GitHub Actions**.

Первая сборка в CI заметно дольше локальной: Tycho скачивает целевую платформу EDT; дальше её сохраняет кэш Maven (`setup-java` cache).

## Структура

```text
bom/                                      версии Tycho и настройки Java
targets/default/default.target            целевая платформа EDT 2026.2 + Eclipse 2025-12
bundles/io.github.fastsync.edt/            OSGi-плагин (команда, оркестратор, ibcmd)
features/io.github.fastsync.edt.feature/   Eclipse feature
repositories/io.github.fastsync.edt.repository/ p2 update site
tests/io.github.fastsync.edt.tests/        PDE/JUnit plug-in tests
specs/001-ibcmd-source-load/               спека, план, задачи, исследования
.github/workflows/build.yml                CI: сборка, тесты, публикация update site
.github/pages/index.html                   стартовая страница GitHub Pages
```

## Документация

- [1C:EDT Plugin Developer Guide](https://edt.1c.ru/dev/ru/)
- [JavaDoc 1C:EDT 2024.2](https://edt.1c.ru/dev/edt/2024.2/apidocs/) — JavaDoc API плагинов EDT
- [JavaDoc 1C:EDT 2025.2](https://edt.1c.ru/dev/edt/2025.2/apidocs/) — онлайн JavaDoc для 2026.2 пока не опубликован по ожидаемому адресу; сверяйте API с установленной целевой платформой
- [История версий 1C:EDT 2026.2](https://edt.1c.ru/docs/new/versiya-2026-2/)
- [Пример target-файла EDT 2026.2](https://github.com/1C-Company/dt-example-plugins/blob/master/targets/default/default.target)
- [Официальный пример плагинов](https://github.com/1C-Company/dt-example-plugins/tree/master/)

## Лицензия

Проект распространяется по лицензии [MIT](LICENSE): код можно свободно использовать, изменять и распространять, в том числе в составе закрытых продуктов, при сохранении уведомления об авторских правах и текста лицензии.

Сторонние материалы в репозиторий не входят. Локальная выгрузка справки `ibcmd` (`ibcmd-doc.txt`, © 1C-Soft LLC) не коммитится — её можно получить командой `ibcmd --help`.
