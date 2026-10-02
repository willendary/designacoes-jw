plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "br.com.willendary.designacoesjw"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.willendary.designacoesjw"
        minSdk = 26
        targetSdk = 35
        versionCode = 34
        versionName = "0.4.3"
    }

    // ── Assinatura ──────────────────────────────────────────────────────────
    //
    // NÃO defina signingConfig aqui de propósito.
    //
    // A assinatura é feita pelo CI (.github/workflows/android.yml), que
    // decodifica o secret ANDROID_KEYSTORE_BASE64 e re-assina o APK. A chave
    // de release só existe dentro do GitHub e nunca chega a esta máquina.
    //
    // Um bloco de signing release local com queda para a chave de debug — que
    // foi o que chegou a existir aqui — é a pior das opções: compila sem
    // erro e gera um APK que o Android só aceita por cima de outro APK
    // assinado com a mesma chave de debug. Foi exatamente o que causou o
    // erro "App not installed" e a confusão de dois APKs na mesma release.
    //
    // Para testar no aparelho, use sempre o designacoes-jw.apk publicado
    // pela release, que vem assinado com a chave certa.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
