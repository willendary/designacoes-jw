# Histórico de versões — Designações JW

## 0.12.4

- Implementa variante Android release e assina APK e Android App Bundle (.aab).
- Atualiza compileSdk/targetSdk para API 36 com Gradle/AGP compatíveis.
- Acrescenta smoke test instrumentado Android e rotina de validação manual do importador real.
- Adiciona checklist de produção e pendências explícitas antes da Play Store.


## 0.12.3

- Sincronização Android exclui somente os documentos removidos explicitamente do cache anterior, sem apagar itens criados em outros aparelhos.
- Falhas de envio no Windows propagam erro e deixam alterações locais pendentes para nova tentativa.
- A sincronização manual no Windows não informa sucesso quando uma escrita na nuvem falha.
- Mantém formatos de dados existentes; conflito simultâneo sobre o mesmo documento ainda requer revisão específica.


## 0.12.2

- Bloqueia criação automática de perfis de visualizador para contas sem convite.
- Valida convite pendente, destinatário e e-mail verificado antes de atribuir permissões.
- Impede autoelevação de privilégios e alteração indevida dos campos de convites.
- Preserva perfis já cadastrados; regras do Firebase precisam ser implantadas separadamente para valer na nuvem.


## 0.12.1

- Simplifica a navegação no Android e Windows com as áreas principais em primeiro plano.
- Esconde ferramentas secundárias da escala atrás de «Mais ações», preservando exportação, estatísticas e recálculo.
- Organiza o menu Windows com ferramentas recolhíveis e evita repetir o cadastro de irmãos.
- Não altera permissões ou dados nesta versão; a revisão de segurança será separada.

## 0.12.0

- Separa o programa das reuniões da escala e do histórico de privilégios em Android e Windows.
- Preserva o conteúdo editorial importado, incluindo referências clicáveis, imagens e legendas.
- Lê durações abaixo dos títulos e mantém cânticos/comentários sem criar posições extras de designação.
- Reimportação mantém participantes em partes numeradas de título repetido; HTML não reconhecido não apaga o programa anterior.
- Conteúdo adicional sincronizado entre Android e Windows, com leitura compatível com dados anteriores.

## 0.11.0

- Agenda com busca, filtro de pendências, progresso de preenchimento e navegação inferior no Android.
- Paleta roxa e programa organizado por seções; tema claro/escuro respeita a preferência do aplicativo.
- Completar o mês mantém IDs, datas especiais, bloqueios, programas e designações existentes.
- Recalcular apoios exige confirmação e preserva os participantes do programa.
- Revisão de vagas, ausências e conflitos antes do compartilhamento; preenchimento manual de vagas.
- Seleção por ID evita confundir homônimos; reimportação reconcilia a identidade das partes.
- Corrigidos rótulos literais de recursos, edição pelo calendário, metadados de partes no Firestore e versão do atualizador Windows.
- Removido o gerenciamento duplicado de autorizações por privilégio no desktop.

## 0.10.5

- **Privacidade no Android:** desativados os backups automáticos do sistema para os dados privados do aplicativo.
- **Versões alinhadas:** Android e pacote desktop passam a identificar esta entrega como 0.10.5.
- **Validação de publicação:** a versão só deve ser considerada liberada após o workflow Android concluir build, testes, verificação do APK e assinatura.

## 0.10.4

- Versão anterior; consulte o histórico do repositório para as alterações anteriores.
