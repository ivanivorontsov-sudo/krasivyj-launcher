# Сторонние компоненты / Third-party notices

Компоненты, которые входят в собранное приложение. Полные тексты лицензий доступны по ссылкам; лицензия Apache 2.0 приложена в файле `licenses/Apache-2.0.txt`.

## Android Jetpack (AndroidX) / Material Components, © The Android Open Source Project / Google LLC
- Лицензия: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
- Артефакты: `androidx.activity:activity-compose:1.9.3`, `androidx.compose.foundation:foundation`, `androidx.compose.material3:material3`, `androidx.compose.ui:ui`, `androidx.compose:compose-bom:2024.10.01`, `androidx.core:core-ktx:1.13.1`, `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`, `androidx.metrics:metrics-performance:1.0.0-beta01`, `androidx.profileinstaller:profileinstaller:1.4.1`

## Kotlin / kotlinx, © JetBrains s.r.o. and Kotlin contributors
- Лицензия: Apache License 2.0 — https://www.apache.org/licenses/LICENSE-2.0
- Артефакты: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`, `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`

## JSON-java (org.json), Sean Leary and contributors
- Лицензия: Public Domain (версии 20231013 и новее) — https://github.com/stleary/JSON-java/blob/master/LICENSE
- Артефакты: `org.json:json:20231013`

## Транзитивные зависимости
Транзитивные зависимости перечисленных библиотек AndroidX/Kotlin распространяются по Apache License 2.0 (проверено по POM-файлам Google Maven).

## Только для тестов (в приложение не входят)
`org.jetbrains.kotlin:kotlin-test:2.0.21` — JUnit 4: Eclipse Public License 1.0; Robolectric: MIT; остальные: Apache 2.0.


## Шрифты
Встроенные шрифты (`app/src/main/res/font/f_*.ttf`) получены из Google Fonts и распространяются по SIL Open Font License 1.1 (текст в `app/src/main/assets/OFL.txt`, исходники с файлами OFL.txt каждого семейства — в `tools/fonts-src/raw/`). Шрифты не продаются отдельно; переименованные файлы не используют зарезервированные названия шрифтов в качестве имени продукта.


## Шрифты, иконки, изображения, звуки
Иконки приложения нарисованы для проекта (векторные). Значки интерфейса — Material Icons (Apache 2.0), если используются. Сторонние изображения, звуки и логотипы чужих брендов в приложении не используются, если выше не указано иное.
