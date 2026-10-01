import java.util.Properties

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Client OAuth do tipo Desktop (loopback). O secret nao pode ir no codigo:
// o repositorio e publico, e commit nao tem volta. Ele entra no build a
// partir de local.properties (ja no .gitignore) ou da variavel de ambiente
// GOOGLE_CLIENT_SECRET, e e gerado como recurso do jar — nunca versionado.
//
// Sem isso o login com Google no desktop simplesmente nao funciona, e a
// mensagem na tela explica isso em vez de estourar um invalid_request cru.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val googleClientId = localProps.getProperty("google.client.id")
    ?: System.getenv("GOOGLE_CLIENT_ID")
    ?: "859002390487-59pgf9q7t07g0ie86v52226g6u9pf7pc.apps.googleusercontent.com"
val googleClientSecret = localProps.getProperty("google.client.secret")
    ?: System.getenv("GOOGLE_CLIENT_SECRET")
    ?: ""

val generateGoogleAuthConfig by tasks.registering {
    val id = googleClientId
    val secret = googleClientSecret
    val outDir = layout.buildDirectory.dir("generated/googleauth")
    inputs.property("id", id)
    inputs.property("secret", secret)
    outputs.dir(outDir)
    doLast {
        val target = outDir.get().asFile.resolve("googleauth.properties")
        target.parentFile.mkdirs()
        target.writeText("clientId=$id\nclientSecret=$secret\n")
    }
}

// srcDir com provider nao registra a dependencia: a task rodava so com
// "gradle <task>". Precisamos ligar no processResources explicitamente.
tasks.named<ProcessResources>("processResources") {
    dependsOn(generateGoogleAuthConfig)
    from(generateGoogleAuthConfig)
}


dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

compose.desktop {
    application {
        mainClass = "br.com.willendary.designacoesjw.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = "DesignacoesJW"
            packageVersion = "0.3.2"
        }
    }
}
