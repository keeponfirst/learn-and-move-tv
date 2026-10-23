# 第三方軟體清單（Third-party software）

下列套件隨 APK 散布。除了一個套件採 BSD 3-Clause，其餘全部採 Apache License 2.0。

- Apache License 2.0 全文：`Apache-2.0.txt`
- BSD 3-Clause（protobuf）全文：`BSD-3-Clause-protobuf.txt`
- 兩個檔在 APK 的 `assets/licenses/`，在 repo 的 `LICENSES/`。

All packages below ship in the APK. One uses BSD 3-Clause; all others use Apache License 2.0.

清單由 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 產生。
授權依各套件 Maven POM 的 `<licenses>` 欄位。

| 套件 | 版本 | 授權 | 著作權人 |
|---|---|---|---|
| androidx.activity:activity | 1.10.1 | Apache-2.0 | The Android Open Source Project |
| androidx.activity:activity-compose | 1.10.1 | Apache-2.0 | The Android Open Source Project |
| androidx.activity:activity-ktx | 1.10.1 | Apache-2.0 | The Android Open Source Project |
| androidx.annotation:annotation | 1.9.1 | Apache-2.0 | The Android Open Source Project |
| androidx.annotation:annotation-experimental | 1.4.1 | Apache-2.0 | The Android Open Source Project |
| androidx.annotation:annotation-jvm | 1.9.1 | Apache-2.0 | The Android Open Source Project |
| androidx.arch.core:core-common | 2.2.0 | Apache-2.0 | The Android Open Source Project |
| androidx.arch.core:core-runtime | 2.2.0 | Apache-2.0 | The Android Open Source Project |
| androidx.autofill:autofill | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.collection:collection | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| androidx.collection:collection-jvm | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| androidx.collection:collection-ktx | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.animation:animation | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.animation:animation-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.animation:animation-core | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.animation:animation-core-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.foundation:foundation | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.foundation:foundation-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.foundation:foundation-layout | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.foundation:foundation-layout-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.material:material-icons-core | 1.7.8 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.material:material-icons-core-android | 1.7.8 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime-annotation | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime-annotation-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime-retain | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime-retain-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime-saveable | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.runtime:runtime-saveable-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-geometry | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-geometry-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-graphics | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-graphics-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-text | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-text-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-tooling-preview | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-tooling-preview-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-unit | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-unit-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-util | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.compose.ui:ui-util-android | 1.11.4 | Apache-2.0 | The Android Open Source Project |
| androidx.concurrent:concurrent-futures | 1.1.0 | Apache-2.0 | The Android Open Source Project |
| androidx.core:core | 1.16.0 | Apache-2.0 | The Android Open Source Project |
| androidx.core:core-ktx | 1.16.0 | Apache-2.0 | The Android Open Source Project |
| androidx.core:core-viewtree | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.customview:customview-poolingcontainer | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-android | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-core | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-core-android | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-core-okio | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-core-okio-jvm | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-preferences | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-preferences-android | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-preferences-core | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-preferences-core-android | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.datastore:datastore-preferences-external-protobuf | 1.1.7 | BSD-3-Clause | Google Inc.（protobuf-lite，由 The Android Open Source Project 重新打包） |
| androidx.datastore:datastore-preferences-proto | 1.1.7 | Apache-2.0 | The Android Open Source Project |
| androidx.documentfile:documentfile | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.dynamicanimation:dynamicanimation | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.emoji2:emoji2 | 1.4.0 | Apache-2.0 | The Android Open Source Project |
| androidx.graphics:graphics-path | 1.0.1 | Apache-2.0 | The Android Open Source Project |
| androidx.interpolator:interpolator | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.legacy:legacy-support-core-utils | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-common | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-common-jvm | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-livedata | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-livedata-core | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-livedata-core-ktx | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-process | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-android | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-compose | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-compose-android | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-runtime-ktx-android | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-android | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-savedstate | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-android | 2.9.4 | Apache-2.0 | The Android Open Source Project |
| androidx.loader:loader | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.localbroadcastmanager:localbroadcastmanager | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.print:print | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.profileinstaller:profileinstaller | 1.4.1 | Apache-2.0 | The Android Open Source Project |
| androidx.savedstate:savedstate | 1.3.2 | Apache-2.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-android | 1.3.2 | Apache-2.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-compose | 1.3.2 | Apache-2.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-compose-android | 1.3.2 | Apache-2.0 | The Android Open Source Project |
| androidx.savedstate:savedstate-ktx | 1.3.2 | Apache-2.0 | The Android Open Source Project |
| androidx.startup:startup-runtime | 1.1.1 | Apache-2.0 | The Android Open Source Project |
| androidx.tracing:tracing | 1.2.0 | Apache-2.0 | The Android Open Source Project |
| androidx.transition:transition | 1.6.0 | Apache-2.0 | The Android Open Source Project |
| androidx.tv:tv-foundation | 1.0.0 | Apache-2.0 | The Android Open Source Project |
| androidx.tv:tv-material | 1.1.0 | Apache-2.0 | The Android Open Source Project |
| androidx.versionedparcelable:versionedparcelable | 1.1.1 | Apache-2.0 | The Android Open Source Project |
| androidx.window:window | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| androidx.window:window-core | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| androidx.window:window-core-android | 1.5.0 | Apache-2.0 | The Android Open Source Project |
| com.google.guava:listenablefuture | 1.0 | Apache-2.0 | The Guava Authors |
| com.squareup.okio:okio | 3.4.0 | Apache-2.0 | Square, Inc. |
| com.squareup.okio:okio-jvm | 3.4.0 | Apache-2.0 | Square, Inc. |
| org.jetbrains:annotations | 23.0.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlin:kotlin-android-extensions-runtime | 1.9.22 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlin:kotlin-parcelize-runtime | 1.9.22 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlin:kotlin-stdlib | 2.2.21 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlin:kotlin-stdlib-common | 2.2.21 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlin:kotlin-stdlib-jdk7 | 1.8.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlin:kotlin-stdlib-jdk8 | 1.8.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.9.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlinx:kotlinx-coroutines-core | 1.9.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.9.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlinx:kotlinx-serialization-core | 1.9.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm | 1.9.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.9.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jetbrains.kotlinx:kotlinx-serialization-json-jvm | 1.9.0 | Apache-2.0 | JetBrains s.r.o. and respective authors and developers |
| org.jspecify:jspecify | 1.0.0 | Apache-2.0 | The JSpecify Authors |

## Kotlin standard library 內含的第三方程式碼

- `kotlin.time` 部分程式碼：Copyright (c) 2007-present, Stephen Colebourne & Michael Nascimento Santos。採 BSD 3-Clause License。
- `MathJVM.kt` 部分程式碼：採 Boost Software License 1.0。
- 部分集合與無號數程式碼：衍生自 GWT（Copyright 2007-2008 Google Inc.）與 Guava，採 Apache License 2.0。
- 授權全文見：https://github.com/JetBrains/kotlin/tree/master/license/third_party

## 不隨 APK 散布

- JUnit 4.13.2（Eclipse Public License 1.0）：只在建置測試時使用。
- androidx.compose.ui:ui-tooling：只進 debug APK。
- Gradle Wrapper（`gradle/wrapper/`，Apache License 2.0）：只在建置時使用。
