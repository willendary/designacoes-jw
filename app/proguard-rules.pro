# Regras do ProGuard/R8.
#
# minify esta desligado, entao este arquivo e o lugar onde os keeps entram
# quando ele for ligado. O que importa agora:
#
# - Modelos e serializers: kotlinx.serialization le os metadados por
#   reflexao. Se um dia o R8 for ligado e o modelo mudar de nome no
#   servidor, o JSON antigo deixa de ser lido e a tela mostra lista vazia.
# - Os nomes dos campos sao o contrato com o Firestore. Renomear um campo em
#   Kotlin sem renomear o documento no Firestore perde dado.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class br.com.willendary.designacoesjw.**$$serializer { *; }
-keepclassmembers class br.com.willendary.designacoesjw.** {
    *** Companion;
}
-keepclasseswithmembers class br.com.willendary.designacoesjw.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum do modelo: o valor gravado no Firestore e o nome da constante.
-keepclassmembers enum br.com.willendary.designacoesjw.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
