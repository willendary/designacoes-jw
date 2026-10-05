package br.com.willendary.designacoesjw.importer

/**
 * O prompt para a IA, e onde abrir cada conversa.
 *
 * ## Por que o prompt mora no app
 *
 * O texto é o contrato entre a IA e o [ImportadorDesignacoes]. Se cada um
 * escrever o pedido do seu jeito, cada uma devolve um formato diferente e o
 * interpretador passa a adivinhar. O prompt é a versão que produz
 * `data - nome - parte`, que é o que o parser entende.
 *
 * ## Por que "abrir" e não "abrir já com o texto"
 *
 * ChatGPT e Gemini **não documentam** um parâmetro de URL que pré-preencha a
 * caixa de texto. Existe `?q=` em alguns clientes, mas não é garantido e muda
 * conforme o produto. Prometer preenchimento automático seria prometer algo que
 * às vezes não acontece — e o usuário perceberia na hora que a caixa veio vazia.
 *
 * Então o caminho é o que funciona sempre: **um clique copia o prompt**, e o
 * botão ao lado abre a conversa. Do ponto de vista de quem usa, são dois cliques
 * e um Ctrl+V, e o texto está lá.
 *
 * A ordem dos botões importa: abrir primeiro e copiar depois deixa a janela do
 * navegador na frente do app, e trocar de janela para colar é o atrito que a
 * pessoa não devia ter.
 */
object PromptImportacao {

    /**
     * O prompt.
     *
     * Três pedidos e três proibições, nessa ordem:
     *
     * - **uma parte por linha**, com o formato literal — é o que o parser lê;
     * - **a data da quarta** daquela semana, porque a lista é da reunião de meio
     *   de semana e sem data a linha não tem para onde ir;
     * - **o título da parte como está no programa**, sem número e sem os minutos,
     *   porque é assim que o app casa com o item importado do jw.org;
     * - **nada além das linhas** — sem cabeçalho, sem "total", sem tabela, sem
     *   markdown. Uma tabela vira uma linha por célula e o interpretador trata
     *   cada uma como uma designação.
     *
     * O exemplo é o contrato executável: uma IA que copia o exemplo produz
     * exatamente o que o parser espera.
     */
    const val TEXTO = """Preciso que você leia a lista de partes de reuniões anexada e me devolva as designações, uma parte por linha.

Formato de cada linha, exatamente assim:

DD/MM - Nome Completo do Irmão - Título da Parte

Regras:
- DD/MM é a data da quarta-feira da semana. Use 07/10, e não 7/10.
- O nome é o nome completo, exatamente como está na lista.
- O título da parte é como está no programa da reunião, sem o número na frente e sem o tempo entre parênteses.
- Uma parte por linha, uma linha por parte.
- Se uma parte não tiver ninguém, não escreva a linha.

Responda SOMENTE com as linhas. Sem explicação, sem cabeçalho, sem total, sem tabela, sem formatação.

Exemplo do formato da resposta:
07/10 - João da Silva - Demonstração
07/10 - Maria Souza - Leitura do livro
14/10 - João da Silva - Joias espirituais"""

    /** Conversas já existentes, para abrir e continuar. */
    object Conversa {
        const val CHATGPT = "https://chatgpt.com/"
        const val GEMINI = "https://gemini.google.com/app"
        const val DEEPSEEK = "https://chat.deepseek.com/"
    }
}