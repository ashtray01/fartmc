# Лицензии и состав публикации

Проверено 2026-10-20. Это журнал происхождения и условий, а не гарантия
отсутствия любых прав третьих лиц.

| Компонент | Условия / решение |
| --- | --- |
| Новая реализация FartMC, тесты, скрипты, документация | GPL-3.0-only, полный текст LICENSE |
| Собственные процедурные звуки и метаданные публичного пака | CC0-1.0, полный текст licenses/CC0-1.0.txt и LICENSE.txt внутри ZIP |
| FartPlugin 1.2 Denji, предоставленный пользователем | Modrinth API: LicenseRef-All-Rights-Reserved; не распространяется |
| Семь записей Myinstants | Разрешения на перепубликацию не установлены; исключены из CI и публичного ZIP |
| Paper API | provided, GPLv3/MIT в upstream; классы не включаются в JAR |
| Adventure | provided через Paper, MIT; классы не включаются в JAR |
| JUnit | test scope, EPL-2.0; не включается в JAR |
| Maven | Apache-2.0, только инструмент сборки |
| FFmpeg | LGPL/GPL в зависимости от сборки; только внешний кодировщик, не распространяется с артефактами |
| Java, GitHub Actions | Инструменты среды; не включаются в артефакты |
| Minecraft/Paper server, оригинальный ресурспак | Не включаются в Git, JAR, ZIP или артефакты CI |

Новая реализация написана по изученному поведению. Байткод оригинала
был изучен тем же разработчиком; это **не clean-room** разработка.
Права на оригинал не переобъявляются. Исходные классы Denji, bStats и
Dropbox-пак не копируются в артефакты. Для устранения ложного указания
авторства Java package изменён на `io.github.ashtray01.fartmc`,
автор новой версии — ashtray01; Denji указан в NOTICE как автор оригинала.
Имя Bukkit-плагина оставлено ради совместимости конфигурации.

Проверенные источники:

- [Modrinth FartPlugin](https://modrinth.com/plugin/fartplugin);
  [API проекта RfQBoeO5](https://api.modrinth.com/v2/project/RfQBoeO5):
  `license.id = LicenseRef-All-Rights-Reserved`, source_url отсутствует.
- [Hangar FartPlugin](https://hangar.papermc.io/Denji/FartPlugin): License Unspecified.
- [Myinstants Terms of Use](https://www.myinstants.com/en/terms_of_use.html):
  лицензия загрузившего контент сайту не означает лицензию нам на перепубликацию.
  Кнопка Download и ссылка на автора сами по себе не подтверждают такое право.
- [Paper LICENSE](https://github.com/PaperMC/Paper/blob/main/LICENSE.md).
- [Adventure LICENSE](https://github.com/KyoriPowered/adventure/blob/main/LICENSE).
- [JUnit LICENSE](https://github.com/junit-team/junit5/blob/main/LICENSE.md).
- [FFmpeg legal](https://ffmpeg.org/legal.html).
- [GPLv3](https://www.gnu.org/licenses/gpl-3.0.html);
  [CC0](https://creativecommons.org/publicdomain/zero/1.0/).

## Публикуемые файлы

GitHub Actions создаёт `fartmc-plugin` (JAR, LICENSE, NOTICE, соответствующие
исходники `fartmc-source.zip`, SHA256SUMS) и `fartmc-sounds`
(`fartmc-sounds.zip` и SHA256SUMS). Пак содержит семь OGG, их определения,
CC0, уведомление и манифест с контрольными суммами.
Сборка не создаёт GitHub Release автоматически; артефакты хранятся 30 дней.
Скачанный из Actions внешний ZIP нужно распаковать, чтобы получить
вложенный `fartmc-sounds.zip`, пригодный для Minecraft.

Проверка `prepare-distribution.ps1` запрещает посторонние классы и файлы
в JAR, неизвестные/дублирующиеся файлы в паке и отслеживаемые бинарники.
Публичный пак собирается в новой пустой папке; локальный кэш Myinstants
никогда не используется. Генератор использует только осцилляторы и шум
с фиксированным seed, без загруженных сэмплов.

При ручном распространении JAR сохраняйте LICENSE/NOTICE и предоставляйте
исходники именно этой сборки согласно GPLv3. Ссылка на ветку с другой версией
не заменяет соответствующие исходники. Не добавляйте сторонние записи,
пока не сохранены условия, подтверждающие право на их распространение.
