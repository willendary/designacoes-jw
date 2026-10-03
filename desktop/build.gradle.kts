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

// Gerado como FONTE KOTLIN, nao como recurso.
//
// A primeira versao escrevia um googleauth.properties e o adicionava ao
// processResources. Compilava, o jar de desenvolvimento tinha o arquivo, e o
// app empacotado abria com o secret vazio — o empacotador nao levou o
// recurso. Fonte compilada nao depende de empacotamento nenhum: o valor vai
// para o bytecode da classe, que vai para o jar de qualquer jeito.
//
// A exposicao do secret e a mesma nos dois casos (esta no binario entregue);
// o que muda e a confianca de ele estar la.
val generateGoogleAuthConfig by tasks.registering {
    val id = googleClientId
    val secret = googleClientSecret
    val outDir = layout.buildDirectory.dir("generated/googleauth")
    inputs.property("id", id)
    inputs.property("secret", secret)
    outputs.dir(outDir)
    doLast {
        val target = outDir.get().asFile.resolve("GoogleAuthConfig.kt")
        target.parentFile.mkdirs()
        target.writeText(
            """
            |package br.com.willendary.designacoesjw.desktop.firebase
            |
            |// Arquivo GERADO em tempo de build. Nao editar a mao.
            |// O secret vem de local.properties (ignorado pelo git) ou da
            |// variavel de ambiente GOOGLE_CLIENT_SECRET.
            |internal object GoogleAuthConfig {
            |    const val CLIENT_ID: String = "$id"
            |    const val CLIENT_SECRET: String = "$secret"
            |}
            """.trimMargin()
        )
    }
}

kotlin.sourceSets["main"].kotlin.srcDir(generateGoogleAuthConfig)



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
            packageVersion = "0.6.2"
        }
    }
}
