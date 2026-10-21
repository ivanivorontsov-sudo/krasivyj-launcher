# Сторонние компоненты / Third-party notices

Компоненты, которые входят в собранное приложение «Мудрость». Полные тексты лицензий лежат в `app/src/main/assets/licenses/` и показываются в приложении: «Поиск → О приложении → Сторонние компоненты и лицензии».

## Модель rubert-tiny2
- Автор: David Dale (cointegrated), https://huggingface.co/cointegrated/rubert-tiny2
- Лицензия: MIT (по карточке модели)
- В приложении: квантованная ONNX-версия модели (`assets/model/rubert_tiny2_int8.onnx`), словарь токенизатора (`assets/model/vocab.txt`) и заранее посчитанные эмбеддинги (`assets/emb.bin`).

## ONNX Runtime
- `com.microsoft.onnxruntime:onnxruntime-android:1.30.0`, © Microsoft Corporation
- Лицензия: MIT, https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- Сведения о компонентах внутри ONNX Runtime: https://github.com/microsoft/onnxruntime/blob/main/ThirdPartyNotices.txt

## Android Jetpack (AndroidX), Jetpack Compose, Material 3, Material Icons
- © The Android Open Source Project / Google LLC
- Лицензия: Apache License 2.0
- Артефакты: `androidx.compose:compose-bom:2024.12.01`, `androidx.compose.ui:ui`, `androidx.compose.ui:ui-tooling-preview`, `androidx.compose.material3:material3`, `androidx.compose.material:material-icons-extended`, `androidx.core:core-ktx:1.15.0`, `androidx.activity:activity-compose:1.9.3`, `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7`, `androidx.lifecycle:lifecycle-runtime-compose:2.8.7`

## Kotlin и kotlinx
- © JetBrains s.r.o. и участники проекта Kotlin
- Лицензия: Apache License 2.0
- Артефакты: стандартная библиотека Kotlin, `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`, `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`

## Транзитивные зависимости
Транзитивные зависимости перечисленных библиотек AndroidX, Kotlin и kotlinx распространяются по Apache License 2.0.

## Только для сборки и тестов (в приложение не входят)
`androidx.compose.ui:ui-tooling` (только debug), JUnit 4 (Eclipse Public License 1.0), `kotlin-test` (Apache 2.0), `com.microsoft.onnxruntime:onnxruntime` (MIT).

## Тексты и цитаты
Цитаты принадлежат их авторам и приводятся в информационных целях с указанием автора и источника.
