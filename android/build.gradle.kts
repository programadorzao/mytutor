// O Kotlin já vem embutido no Android Gradle Plugin 9; só o plugin do Compose é declarado à parte.
plugins {
    id("com.android.application") version "9.5.3" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
