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
        versionCode = 28
        versionName = "0.3.7"
    }

    // ── Assinatura ──────────────────────────────────────────────────────────
    //
    // Sem isto, todo APK sai assinado com a chave de debug DA MÁQUINA que
    // compilou, e a chave é diferente em cada máquina. O resultado é o erro
    // "App not installed / conflito de pacote" em toda atualização: o
    // Android recusa porque a assinatura não bate com a do app instalado.
    //
    // Nada de segredo aqui: o caminho e as senhas vêm do ambiente, e o
    // release.jks fica fora do git (ver .gitignore). Sem a chave configurada,
    // o build de release usa a debug — o que é aceitável para compilar e
    // testar, mas NÃO para publicar: assim o log abaixo avisa.
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH")
                ?: if (file("${rootDir}/release.jks").exists()) "${rootDir}/release.jks" else null
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null
                || file("${rootDir}/release.jks").exists()
            ) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "ATENÇÃO: build de release SEM chave de release — usando a chave " +
                        "de debug desta máquina. O APK instala apenas por cima de outro " +
                        "APK assinado com a mesma chave. Defina KEYSTORE_PATH, " +
                        "KEYSTORE_PASS, KEY_ALIAS e KEY_PASS, ou crie release.jks na raiz."
                )
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

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
