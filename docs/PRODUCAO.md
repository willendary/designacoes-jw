# Preparação para produção — Designações JW

Última revisão: 01/11/2026. Este documento é uma lista de verificações, **não um certificado de produção**.

## Concluído no código
- [x] Interface simplificada em Android e Windows (0.12.1).
- [x] Regras de Firestore revisadas e testadas no emulador (0.12.2).
- [x] Proteção contra exclusão de dados que não existiam no cache anterior (0.12.3).
- [x] Pipeline de release APK e AAB, com assinatura e verificação (0.12.4).
- [x] Smoke test de abertura Android e teste manual/agendado da importação da publicação no CI.

## Bloqueadores para lançamento público
- [ ] **Publicar as regras** no projeto Firebase real pelo workflow manual, verificar credenciais e testar usuários autorizados e não autorizados.
- [ ] **Isolamento por congregação/workspace** se o app aceitar mais de uma congregação. Atualmente o caminho workspaces/designacoes-jw é fixo.
- [ ] **Reconciliação concorrente** de edições no mesmo documento e testes de backup/restauração.
- [ ] Homologar login, convites, permissões, offline/sincronização, exclusões e atualização em aparelhos reais, incluindo Android 16 e Windows.
- [ ] Validar comparação editorial com a publicação oficial e fluxo da semana real em diferentes datas.
- [ ] Criar política de privacidade pública com dados de contato reais, processo de exclusão de conta e avaliação LGPD.
- [ ] Confirmar uso adequado de nomes, identidade visual e conteúdo editorial de terceiros.
- [ ] Configurar Play Console, informações da listagem, App Signing, ficha de segurança de dados e testes fechados.
- [ ] Conferir assinaturas do novo AAB, versão Android, atualização sobre APK anterior e rollback.

## Como validar
1. Executar os workflows Android Release, Desktop, UI, Firestore rules e Smoke Android.
2. Validar a versão baixada no dispositivo, sem desinstalar a anterior.
3. Executar separadamente o workflow de implantação Firestore após revisão.
4. Executar importação real do conteúdo da publicação e comparar conteúdo e tela.
5. Só enviar o AAB ao Google Play após concluir os bloqueadores.

A existência de uma GitHub Release não significa que o aplicativo esteja aprovado para produção.
