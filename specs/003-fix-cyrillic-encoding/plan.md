# Implementation Plan: корректное отображение кириллицы в установщике и консоли

**Branch**: `003-fix-cyrillic-encoding` (рабочая ветка `master`) | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-fix-cyrillic-encoding/spec.md`

## Summary

Два дефекта кодировки с одной природой «текст в UTF-8 читают как однобайтовую кодировку»:

1. Локализованные `.properties` (название/описание фичи и бандла, сообщения) хранятся сырым UTF-8, а Tycho/p2 читает их `java.util.Properties.load` как ISO-8859-1 → в каталоге обновлений название фичи сохраняется как `FastSync Ð´Ð»Ñ 1C:EDT`. Исправление: хранить локализуемые файлы в ASCII с `\uXXXX`-экранированием (такой файл корректно читают все потребители: p2, OSGi, ResourceBundle).
2. `MessageConsole` создаётся без кодировки, поэтому Eclipse берёт `WorkbenchEncoding.getWorkbenchDefaultEncoding()` (на русской Windows — windows-1251), а плагин пишет в поток UTF-8-байты (ConsoleOutput.java:43) → в консоли «Р—Р°РїСѓСЃРє». Исправление: создавать консоль с явной кодировкой UTF-8; `IOConsole` в целевой платформе не имеет `setEncoding`, кодировка фиксируется в конструкторе (проверено байткодом 3.15.0).

Технические решения — [research.md](research.md); модель ресурсов и связей — [data-model.md](data-model.md); контракт локализованных метаданных — [contracts/localized-metadata.md](contracts/localized-metadata.md); проверка — [quickstart.md](quickstart.md).

## Technical Context

**Language/Version**: Java 25 (bytecode level 25)

**Primary Dependencies**: Eclipse `org.eclipse.ui.console` 3.15.0 (Eclipse 2025-12) — публичные `MessageConsole(String, String, ImageDescriptor, boolean)` и `IOConsole#getEncoding()`; Tycho 5.0.2 / p2 publisher; новых зависимостей нет

**Storage**: N/A (локализуемые `.properties`, p2-метаданные сборки)

**Testing**: PDE/JUnit 4 в `tests/io.github.fastsync.edt.tests` (чтение `.properties` как ISO-8859-1, кодировка консоли), shell-проверка ASCII в `scripts/check-encoding.sh`; ручная проверка на Windows и в EDT с русским UI

**Target Platform**: 1C:EDT 2026.2.2 (Java 25), Eclipse SimRel 2025-12; разработка на macOS, целевые пользователи — Windows с русской локалью

**Project Type**: многомодульный Eclipse/OSGi плагин (Tycho: bom, targets, bundles, features, repositories, tests)

**Performance Goals**: N/A — исправление отображения, объём вывода не меняется

**Constraints**: только публичные API; без новых зависимостей; состав/порядок строк консоли и отчёта и маскирование секретов не меняются; английская локаль не меняется; сборка `./scripts/build.sh`; CI вызывает `mvn verify` напрямую

**Scale/Scope**: 3 локализованных `.properties`, 1 создание консоли, 2 новых теста, 1 shell-проверка

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Ворота | Статус | Обоснование |
|--------|--------|-------------|
| I. Только публичные API EDT/Eclipse | PASS | `MessageConsole(...)` и `IOConsole#getEncoding()` — публичные API, наличие проверено `javap` в целевой платформе 3.15.0; внутренние пакеты не импортируются |
| II. Безопасность ИБ, исходников и секретов | PASS | Данные и временные файлы не затрагиваются; строки и маскирование (`IbcmdRunner.mask`) не меняются |
| III. Контракт целевой платформы | PASS | Версии и target не меняются; новая зависимость только в тестовом бандле (`org.eclipse.ui.console`, тот же публичный бандл, что у основного) |
| IV. Проверка до готовности | PASS | PDE-тесты: чтение ресурсов как ISO-8859-1 (ловит сырую кириллицу), кодировка консоли; ASCII-проверка в сборке и CI; `./scripts/build.sh`; ручные проверки (Windows, установка из p2) фиксируются в [tasks.md](tasks.md) |
| V. Локализация и осмысленный UX | PASS | Русские строки становятся читаемыми; английские не меняются; строки остаются в `.properties` |
| Доп. ограничения: минимальный объём | PASS | Без новых абстракций и настроек: экранирование существующих файлов, один аргумент конструктора, один shell-скрипт |

**Post-design re-check (after Phase 1)**: PASS — дизайн не добавляет зависимостей в runtime, не расширяет экспортируемые пакеты (тест консоли идёт через `Bundle#loadClass`, чтобы не экспортировать `ui`), не меняет поведение загрузки.

## Project Structure

### Documentation (this feature)

```text
specs/003-fix-cyrillic-encoding/
├── plan.md              # этот файл
├── research.md          # Phase 0: причины, решения, проверки API
├── data-model.md        # Phase 1: ресурсы, кодировки, матрица проверок
├── contracts/           # Phase 1: контракт p2-метаданных и консоли
├── quickstart.md        # Phase 1: проверка end-to-end
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
features/io.github.fastsync.edt.feature/feature_ru.properties        # экранировать в ASCII
bundles/io.github.fastsync.edt/plugin_ru.properties                 # экранировать в ASCII
bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/messages_ru.properties  # экранировать в ASCII
bundles/io.github.fastsync.edt/src/io/github/fastsync/edt/ui/ConsoleOutput.java   # явная кодировка UTF-8
tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/LocalizationTest.java   # новый
tests/io.github.fastsync.edt.tests/src/io/github/fastsync/edt/tests/ConsoleEncodingTest.java # новый
tests/io.github.fastsync.edt.tests/META-INF/MANIFEST.MF              # + org.eclipse.ui.console
scripts/check-encoding.sh                                            # новый: ASCII-проверка ресурсов
scripts/build.sh                                                     # вызов проверки перед mvn
.github/workflows/build.yml                                          # шаг проверки (CI вызывает mvn напрямую)
AGENTS.md                                                            # правило хранения локализуемых ресурсов
```

## Complexity Tracking

Нарушений конституции нет, отступления не требуются.
