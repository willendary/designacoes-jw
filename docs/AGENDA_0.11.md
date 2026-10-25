# Agenda 0.11 — comportamento e validação

A agenda reúne a programação, as vagas e a revisão de pendências. O visual usa roxo discreto, superfícies simples e seções do programa; a identidade e o nome do aplicativo continuam próprios. Referências de organização: [Hourglass](https://hourglass-app.com/pt-br/features/) e [JW Library](https://www.jw.org/pt/ajuda-online/jw-library/android/).

## Fluxos

- **Completar escala** cria os encontros que faltam e preenche vagas de apoio. Mantém IDs, programas, participantes escolhidos, bloqueios e reuniões em datas especiais. Executar novamente não duplica a escala.
- **Recalcular apoios** exige confirmação. Recalcula os apoios dos dias configurados, mantendo programa e participantes. Reuniões transferidas para outra data permanecem intactas.
- **A revisar** considera vagas de apoio e de programa, pessoas ausentes/inativas/removidas, acúmulo de designações e programa não importado. O responsável continua decidindo se acumulações são apropriadas.
- **Buscar** procura data, tipo, tema, títulos de partes e participantes de apoios ou do programa; ignora acentos e maiúsculas.
- **Preencher vaga** e **Trocar** validam novamente disponibilidade e habilitação ao salvar. Pessoas no programa não recebem um apoio automaticamente.
- O seletor de participantes usa IDs, preserva homônimos e permite remover uma escolha mesmo depois de a pessoa ficar indisponível.
- Reimportar um programa conserva participantes apenas quando a mesma parte pode ser identificada sem ambiguidade pelo título e seção. Partes removidas/alteradas ficam para revisão; não são preenchidas com participantes de outra posição.
- Autorizações ficam no cadastro das pessoas; a tela duplicada do Windows foi removida sem remover os dados.

## Verificações

```sh
gradle :shared:test :desktop:compileKotlin
gradle assembleDebug
python scripts/check_apk_desktop.py app/build/outputs/apk/debug/app-debug.apk
```

O workflow de interface valida busca, preenchimento, seleção e remoção de homônimos, ausência e remoção de participantes excluídos. Produz capturas reais dos componentes em largura de celular, temas claro/escuro e da agenda desktop no artefato `agenda-interface`.

A suíte compartilhada cobre preservação e idempotência, reuniões transferidas, dias configurados, bloqueios, ausências, homônimos, reimportação, busca e datas inválidas. O CI também produz APK assinado e instaladores Windows.

Para homologação no aparelho: usar uma congregação de teste, alternar claro/escuro, filtrar a agenda, importar uma semana, designar duas pessoas homônimas, registrar ausência, completar duas vezes e conferir o compartilhamento. Validar login e sincronização entre dois aparelhos com contas reais autorizadas. Essas integrações dependem do Firebase e não são substituídas pelos testes de lógica.

A importação integral de parágrafos, imagens e referências da Biblioteca On-line (issue #72) continua fora desta entrega; o aplicativo importa o programa com títulos e durações. Não há integração com contas ou dados do Hourglass.
