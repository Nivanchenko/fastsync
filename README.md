# FastSync для 1C:EDT

Java-плагин для 1C:EDT. Сейчас в нём есть временная read-only команда диагностики публичных API EDT. Целевая среда разработки и сборки — **1C:EDT 2026.1.3**; Target-файл использует p2-репозиторий линейки `2026.1`, в которой публикуются патч-релизы EDT 2026.1.

## Требования

- JDK 17 для Maven/Tycho (локальный скрипт сборки использует `JDK/AxiomJDKPro17.jdk/Contents/Home`; в CI используется Azul Zulu JDK FX);
- Apache Maven 3.9.16 (установка: `./scripts/install-maven.sh`; текущая конфигурация Tycho 4.0.5 падает с Maven 3.10.0);
- 1C:EDT 2026.1.3 для запуска и ручной проверки. Начиная с EDT 2025.1, Java для самой EDT устанавливается вместе с продуктом.

## Установка Maven

Из корня репозитория выполните:

```bash
./scripts/install-maven.sh
```

Скрипт устанавливает Maven 3.9.16 в `~/.local/opt/apache-maven-3.9.16` и проверяет SHA-512 архива. Установленный через Homebrew Maven не удаляется.

## Сборка и тесты

Из корня репозитория:

```bash
./scripts/build.sh
```

Скрипт сам задаёт `JAVA_HOME`, `MAVEN_HOME` и `PATH`, затем запускает `clean verify`. Если Maven ещё не установлен, сначала выполните `./scripts/install-maven.sh`.

Результат p2-сборки появится в `repositories/io.github.fastsync.edt.repository/target/repository/`.

## Запуск в Eclipse PDE

1. Импортируйте проекты из репозитория через **File → Import → Existing Projects into Workspace**.
2. Откройте `targets/default/default.target` в Target Editor, при необходимости авторизуйтесь учётной записью с доступом к p2-репозиторию 1С и выберите **Set as Active Target Platform**. Не сохраняйте учётные данные в репозитории.
3. Для запуска выберите проект `io.github.fastsync.edt` и выполните **Run As → Eclipse Application**.
4. В открывшейся тестовой EDT щёлкните правой кнопкой по проекту и выберите **Inspect FastSync EDT APIs**. Результат появится в **Window → Show View → Error Log**.
5. Plug-in tests запускаются из проекта `io.github.fastsync.edt.tests` через **Run As → JUnit Plug-in Test**.

Для установки собранного плагина в EDT укажите локальный каталог p2-репозитория через **Help → Install New Software**.
Диагностическая команда читает сведения о приложении, файловой ИБ, установленной платформе и подключении Designer Agent; она не меняет базу и не открывает/закрывает сеанс.

GitHub Actions получает доступ к p2-репозиторию через secrets `P2_USER` и `P2_TOKEN`. Добавьте их в настройки репозитория, если учётная запись необходима для загрузки целевой платформы.

## Структура

```text
bom/                                      версии Tycho и настройки Java
targets/default/default.target            целевая платформа EDT 2026.1.3
bundles/io.github.fastsync.edt/            OSGi-плагин
features/io.github.fastsync.edt.feature/   Eclipse feature
repositories/io.github.fastsync.edt.repository/ p2 update site
tests/io.github.fastsync.edt.tests/        PDE/JUnit plug-in tests
```

## Документация

- [1C:EDT Plugin Developer Guide](https://edt.1c.ru/dev/ru/)
- [JavaDoc 1C:EDT 2025.2](https://edt.1c.ru/dev/edt/2025.2/apidocs/) — онлайн JavaDoc для 2026.1 пока не опубликован по ожидаемому адресу; сверяйте API с установленной целевой платформой.
- [История версий 1C:EDT 2026.1, включая 2026.1.3](https://edt.1c.ru/docs/new/versiya-2026-1/)
- [Системные требования 1C:EDT 2026.1.3](https://dl03.1c.ru/content/DevelopmentTools/2026_1_3/requirements.html)
- [Пример target-файла EDT 2026.1](https://github.com/1C-Company/dt-example-plugins/blob/921c30ca006d05895d4d599091ea3e55a8afea69/targets/default/default.target)
- [Официальный пример плагинов](https://github.com/1C-Company/dt-example-plugins/tree/master/)

Лицензия проекта ещё не выбрана; перед распространением добавьте соответствующий файл `LICENSE`.
