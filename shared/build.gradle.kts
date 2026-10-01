plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    // A UI das telas foi parar aqui para que Android e Desktop parem de manter
    // duas implementacoes da mesma coisa (ver issue #31). Ja havia divergido:
    // o mesmo programa era renderizado de formas diferentes nos dois apps.
    id("org.jetbrains.compose") version "1.7.3"
    kotlin("plugin.compose") version "2.0.21"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    testImplementation(kotlin("test"))
}
