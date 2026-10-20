package org.ivanivorontsov.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCategoriesTest {
    @Test fun platformCategoryBeatsKeywords() {
        assertEquals(HomeCategories.GAMES, HomeCategories.folderName("com.example.browser", "Браузер", 0))
        assertEquals(HomeCategories.AUDIO, HomeCategories.folderName("com.example.app", "App", 1))
        assertEquals(HomeCategories.VIDEO, HomeCategories.folderName("com.example.app", "App", 2))
        assertEquals(HomeCategories.IMAGE, HomeCategories.folderName("com.example.app", "App", 3))
        assertEquals(HomeCategories.SOCIAL, HomeCategories.folderName("com.example.app", "App", 4))
        assertEquals(HomeCategories.NEWS, HomeCategories.folderName("com.example.app", "App", 5))
        assertEquals(HomeCategories.MAPS, HomeCategories.folderName("com.example.app", "App", 6))
        assertEquals(HomeCategories.PRODUCTIVITY, HomeCategories.folderName("com.example.app", "App", 7))
        assertEquals(HomeCategories.ACCESSIBILITY, HomeCategories.folderName("com.example.app", "App", 8))
        assertEquals(HomeCategories.FINANCE, HomeCategories.folderName("com.example.app", "App", 42, financeCategory = 42))
    }

    @Test fun keywordsAndLeftovers() {
        assertEquals(HomeCategories.SOCIAL, HomeCategories.folderName("org.telegram.messenger", "Telegram", -1))
        assertEquals(HomeCategories.AUDIO, HomeCategories.folderName("com.spotify.music", "Spotify", -1))
        assertEquals(HomeCategories.VIDEO, HomeCategories.folderName("com.google.android.youtube", "YouTube", -1))
        assertEquals(HomeCategories.IMAGE, HomeCategories.folderName("com.android.camera", "Камера", -1))
        assertEquals(HomeCategories.FINANCE, HomeCategories.folderName("ru.sberbankmobile", "Сбербанк", -1))
        assertEquals(HomeCategories.MAPS, HomeCategories.folderName("ru.yandex.yandexmaps", "Карты", -1))
        assertEquals(HomeCategories.OTHER, HomeCategories.folderName("com.example.unknown", "Заметки нет", -1))
        assertEquals(HomeCategories.OTHER, HomeCategories.folderName("com.android.settings", "Настройки", -1))
    }
}
