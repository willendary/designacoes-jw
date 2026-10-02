#!/usr/bin/env python3
"""
Falha o build se o APK do Android referenciar classe de Compose Desktop.

**Por que isto existe.** `shared` é `kotlin("jvm")` com o plugin Compose
Multiplatform, então compila contra o Compose **desktop**. API de material3
comum (`Text`, `Icon`, `Surface`, botões) funciona nos dois; o que não pode
entrar ali é o que tem implementação por plataforma — `DropdownMenu`, popup,
menu — porque resolve para `Skiko*_skikoKt`, que não existe no APK.

O sintoma é o pior possível: **compila, os testes passam, e o Android morre em
runtime** só quando a tela é desenhada:

    NoClassDefFoundError: Failed resolution of
        androidx/compose/material3/SkikoMenu_skikoKt

Aconteceu na 0.4.2. Nenhum teste unitário pega isso — o `shared` roda em JVM e
a classe desktop existe lá. Só olhando o dex do APK dá para ver.

Uso:
    python scripts/check_apk_desktop.py app/build/outputs/apk/debug/app-debug.apk
"""
import sys
import zipfile

# Prefixos de classe que só existem no Compose Desktop (Skiko) e nunca
# deveriam ser referenciados por um APK Android.
PREFIXOS = (b"Skiko", b"skiko", b"org/jetbrains/skiko")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2

    apk = sys.argv[1]
    try:
        with zipfile.ZipFile(apk) as z:
            dexes = [n for n in z.namelist() if n.endswith(".dex")]
            if not dexes:
                print(f"FALHA: {apk} não tem nenhum .dex — foi empacotado errado?")
                return 1
            achados = []
            for nome in dexes:
                dados = z.read(nome)
                for prefixo in PREFIXOS:
                    if prefixo in dados:
                        achados.append((nome, prefixo.decode(), dados.count(prefixo)))
    except FileNotFoundError:
        print(f"FALHA: {apk} não existe. O build gerou o APK?")
        return 1

    if not achados:
        print(f"OK: {apk} não referencia nenhuma classe do Compose Desktop.")
        return 0

    print(f"FALHA: {apk} referencia classe do Compose Desktop:")
    for nome, prefixo, n in achados:
        print(f"  {nome}: {prefixo} x{n}")
    print()
    print("Causa provável: API de material3 com implementação por plataforma")
    print("(DropdownMenu, popup, menu, diálogo) usada em shared/src/main/kotlin.")
    print("shared é kotlin(\"jvm\") e compila contra o Compose desktop.")
    return 1


if __name__ == "__main__":
    sys.exit(main())