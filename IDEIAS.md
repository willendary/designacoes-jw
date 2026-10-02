# Ideias de implementação — Designações JW

Levantamento de 01/10/2026, depois das releases `0.3.0` a `0.3.7`. Ordenado por
relação entre esforço e valor para a congregação, não por dificuldade técnica.

Nada aqui está implementado. São candidatas a issue quando houver interesse.

---

## Android

### 1. Tela de Irmãos com busca e filtro por grupo

A lista cresce e não tem busca. O desktop tem busca por nome em irmãos e
privilégios; o Android não tem em lugar nenhum. Num grupo de 40+, achar um
irmão vira rolagem.

O mais barato: um `OutlinedTextField` de busca sobre a `LazyColumn` já
existente, com `Modifier.animateItem()` para a lista não pular. Custa poucas
linhas e resolve o caso comum.

### 2. Leitor de codigo de QR no cartaz

Existe o conceito de QR code no hall do Reino (`PublicTalk` tem
`speakerCongregation`, e a tela mostra a congregação do orador). Um QR com a
URL do discurso permitiria ao Irmão ler direto. Mas depende de dado que o
app **não tem** — o link do discurso não vem do jw.org nem é cadastrado. É
feature de dados antes de ser de tela.

### 3. Aviso de reunião amanhã, na tela inicial

Já existe `MeetingReminderHelper.notifyMeeting`. Falta o empurrão: na
`HomeScreen`, quando a reunião é hoje ou amanhã, uma faixa no topo com o
contador de designações e um atalho para o WhatsApp. É o que o usuário mais
abre o app para ver.

### 4. Modo de tela cheia para o cartaz do quadro

Existe o Kiosk (`KioskScreen`) e o `ExportCardImage`. O que falta é o
cartaz do quadro **inteiro** — o mês inteiro em uma imagem, pronto para
imprimir e colar na parede do hall. Hoje dá para exportar a reunião
individual, não o mês. É o `HtmlReportGenerator` renderizado como imagem, o
que o `MeetingCardImage` do `shared` já prepara.

### 5. Widget de próxima reunião na tela inicial do Android

`androidx.glance` ou similar. Mostra "Qarta 07/10 — 12 designações" na tela do
celular sem abrir o app. Trabalho médio, valor alto para quem usa o celular
todo dia.

---

## Desktop

### 6. Desinstalar o Java requirement

Verificar o que o `jpackage` gera: se o `.exe` ainda embute o runtime da JVM
completo (93 MB para um app de 2.900 linhas sugere que sim) ou se dá para
empacotar só o necessário. Com JVM nativa ou trimming agressivo, o instalador
cairia bastante e a instalação ficaria mais rápida.

### 7. Sincronização com indicador de pendência visível

Existe `pendingLocalChange` e `isSyncing`, mas nada mostra "enviando…". Com o
worker de 2 s de carência, o usuário edita, fecha o app e o push pode não ter
saído. **Falta um aviso de "alterações não enviadas"** — é a garantia de que
o dado não se perdeu. Alta prioridade por involve risco de perda.

### 8. Atalho de teclado e navegação por comando

A janela é densa e sem atalhos. Ctrl+S para salvar, Ctrl+F para buscar, F5
para sincronizar, Esc para fechar diálogo. Barato de implementar em Compose
(`Modifier.onPreviewKeyEvent`) e ajuda quem usa o app o dia inteiro.

### 9. Histórico com filtro por irmão

O histórico mostra reuniões do mês. Não há forma de responder "quando o irmão
João leu pela última vez?" sem percorrer todas. A tela de estatísticas de
equidade tem parte disso, mas é mensal e agregada.

---

## Web (Firebase Hosting)

O projeto **tem** `firebase.json` com `hosting.public`, mas o `public/` só tem
o que o Firebase cria por omissão. Não há webapp.

### 10. Painel de leitura para o/android e o desktop compartilharem a mesma tela

O `MeetingProgramList` e o `MeetingCardImage` já estão em `shared` com Compose
Multiplatform. Com `kotlin("js")` ou `wasmJs` no módulo `shared`, o mesmo
composable renderiza no navegador. Uma tela web de **consulta** — só ver, não
editar — resolveria o caso de quem não tem celular nem computador na reunião.

O Firestore já é a fonte única e as `firestore.rules` já restringem escrita por
permissão. Um leitor anônimo precisaria de uma regra `read: if signedIn() &&
activeUser()`, o que já existe.

**Aviso honesto:** isso é o item de maior esforço da lista. Módulo novo no
Gradle, resolução de dependência do Compose para JS, e uma camada de
autenticação web que hoje só existe no Android (Firebase Auth) e no desktop
(OAuth manual). Não é um tarde.

### 11. PWA de leitura offline

Se o webapp existir, service worker para cache do bundle e leitura do último
mês sem rede. Resolve o caso de halls com internet instável. Depende do #10.

### 12. Link de convite por e-mail com QR

Já existe `Invitation` no modelo e o admin cria convite por e-mail. O que falta
é o usuário **usar**: o convite gera uma URL de claim que precisa ser aberta no
navegador. Uma página de claim em `public/` — sem webapp completo, só o fluxo
de convite — seria pequena e já resolveria a administração.

---

## Transversal

### 13. Fila de reconciliação em vez de last-write-wins

Hoje o último a escrever vence, em todo o sistema. Com duas pessoas editando no
mesmo minuto em aparelhos diferentes, a segunda escrita apaga a primeira sem
aviso. Um campo `updatedAt` por documento e uma regra que rejeite escrita com
timestamp mais antigo permitiria detectar o conflito e mostrar "alguém me
alterou, recarregando".

É a evolução natural da issue #10 e da regra de ausência. Não é urgente para uso
de uma pessoa só, mas é o que impede a dor quando a congregação crescer.

### 14. Migração de dados versionada

Cada mudança de modelo teve que escrever leitura tolerante: `ProgramItem` com
duplo formato, `Privilege` com campos padrão, `Meeting.program` de `String` para
objeto. Funcionou, mas não há registro de qual versão um documento está.

Um campo `schemaVersion` no `Store` e um passo explícito de migração por
versão tornaria isso previsível em vez de defensivo. Hoje o custo é
`optString` com default espalhado por três persistências.

### 15. Log de auditoria de quem alterou o quê

Quem designou, quem removeu, quando. Hoje não há rastro. Para uma lista que
organiza a vida da congregação, é informação que oelder responsible
normalmente tem no papel.

Exige campo `alteradoPor` e `alteradoEm` nas escritas — que hoje não têm
ninguém, porque `save()` não sabe quem chamou. Começaria pelo
`AppViewModel`/controller passando o usuário corrente.

---

## Não recomendo

- **Gastar tempo em `strings.xml`** (issue #28) sem plano de traduzir. O ganho
  imediato é consistência de texto, que se resolve com revisão. A issue existe,
  mas a prioridade está errada para um app de uso interno em português.
- **Quebrar `Main.kt` e `App.kt`** (issue #32) sem mudar comportamento junto.
  Refactor puro em 5.800 linhas, com dois apps, não se revisa. Vale quando
  uma feature exigir.
- **Modo KMP de verdade** (issue #31 completo). As telas são genuinamente
  diferentes entre celular e desktop. Unificar o *código* faz sentido; unificar
  o *layout* não.
