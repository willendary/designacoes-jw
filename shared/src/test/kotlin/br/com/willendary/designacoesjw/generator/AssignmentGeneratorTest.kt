package br.com.willendary.designacoesjw.generator

import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.export.CsvDataHandler
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssignmentGeneratorTest {

    @Test
    fun testSentinelReaderInheritsBookReader() {
        // readerGrant e dado, nao nome: o gerador nao deve depender do texto do
        // privilegio. allowedDays continua controlando o dia da semana.
        val sentinelPrivilege = Privilege(
            id = 1, name = "Leitor da Sentinela", allowedDays = setOf(6, 7),
            readerGrant = ReaderGrant.SENTINEL
        )
        val bookPrivilege = Privilege(
            id = 2, name = "Leitor do Livro", allowedDays = setOf(1, 2, 3, 4, 5),
            readerGrant = ReaderGrant.BOOK
        )
        val allPrivileges = listOf(sentinelPrivilege, bookPrivilege)

        val brotherWithSentinel = Brother(id = 10, name = "Joao Silva", isSentinelReader = true)
        val brotherWithBookOnly = Brother(id = 11, name = "Pedro Santos", isReader = true)

        assertTrue(
            AssignmentGenerator.isBrotherAuthorizedForPrivilege(brotherWithSentinel, sentinelPrivilege, allPrivileges)
        )
        // Irmao com Sentinela deve automaticamente poder ler o Livro
        assertTrue(
            AssignmentGenerator.isBrotherAuthorizedForPrivilege(brotherWithSentinel, bookPrivilege, allPrivileges)
        )
        // Irmao só com Livro NÃO pode ler Sentinela
        assertFalse(
            AssignmentGenerator.isBrotherAuthorizedForPrivilege(brotherWithBookOnly, sentinelPrivilege, allPrivileges)
        )
    }

    @Test
    fun testReaderPrivilegesByDayOfWeek() {
        val sentinelPrivilege = Privilege(
            id = 1, name = "Leitor da Sentinela", quantity = 1, allowedDays = setOf(6, 7),
            readerGrant = ReaderGrant.SENTINEL
        )
        val bookPrivilege = Privilege(
            id = 2, name = "Leitor do Livro", quantity = 1, allowedDays = setOf(1, 2, 3, 4, 5),
            readerGrant = ReaderGrant.BOOK
        )
        val privileges = listOf(sentinelPrivilege, bookPrivilege)

        val quartaDate = LocalDate.of(2026, 10, 7) // Quarta-feira (Meio de semana)
        val sabadoDate = LocalDate.of(2026, 10, 10) // Sabado (Fim de semana)

        // Quarta: Leitor do Livro e aplicavel, Sentinela NÃO e aplicavel
        assertTrue(AssignmentGenerator.isPrivilegeApplicableToMeeting(bookPrivilege, quartaDate))
        assertFalse(AssignmentGenerator.isPrivilegeApplicableToMeeting(sentinelPrivilege, quartaDate))

        // Sabado: Leitor da Sentinela e aplicavel, Livro NÃO e aplicavel
        assertTrue(AssignmentGenerator.isPrivilegeApplicableToMeeting(sentinelPrivilege, sabadoDate))
        assertFalse(AssignmentGenerator.isPrivilegeApplicableToMeeting(bookPrivilege, sabadoDate))

        // Irmaos: b1 e leitor do Livro, b2 e leitor da Sentinela
        val b1 = Brother(id = 10, name = "Irmao Livro", isReader = true)
        val b2 = Brother(id = 20, name = "Irmao Sentinela", isSentinelReader = true)
        val brothers = listOf(b1, b2)

        // Na quarta-feira (meio de semana), ambos sao candidatos ao Livro (b2 herda)
        val meetingQuarta = Meeting(id = 1, date = "07/10/2026", type = "Meio de semana")
        val candidatesQuartaLivro = AssignmentGenerator.candidatesFor(meetingQuarta, bookPrivilege.id, 0L, brothers, privileges)
        assertEquals(2, candidatesQuartaLivro.size, "Ambos os irmaos devem ser candidatos para o Livro")

        // No sabado (fim de semana), apenas b2 (Sentinela) e candidato para a Sentinela
        val meetingSabado = Meeting(id = 2, date = "10/10/2026", type = "Fim de semana")
        val candidatesSabadoSentinela = AssignmentGenerator.candidatesFor(meetingSabado, sentinelPrivilege.id, 0L, brothers, privileges)
        assertEquals(1, candidatesSabadoSentinela.size)
        assertEquals(20, candidatesSabadoSentinela[0].id, "Apenas o irmao habilitado para Sentinela deve ler no sabado")
    }

    @Test
    fun testUnavailableBrotherIsExcluded() {
        val privilege = Privilege(id = 1, name = "Indicador", quantity = 1)
        val meetingDate = LocalDate.of(2026, 10, 7) // Quarta-feira

        val brotherOnVacation = Brother(
            id = 10,
            name = "Viajante",
            privileges = setOf(1),
            unavailabilities = listOf(
                UnavailablePeriod(id = 1, startDate = "01/10/2026", endDate = "15/10/2026", reason = "Ferias")
            )
        )
        val availableBrother = Brother(
            id = 11,
            name = "Disponivel",
            privileges = setOf(1)
        )

        val meeting = AssignmentGenerator.generateMeeting(
            date = meetingDate,
            blocked = emptySet(),
            brothers = listOf(brotherOnVacation, availableBrother),
            privileges = listOf(privilege),
            history = emptyList()
        )

        assertEquals(1, meeting.assignments.size)
        assertEquals(11, meeting.assignments[0].brotherId, "Irmao em ferias nao deveria ser designado")
    }

    @Test
    fun testBlockedBrotherIsExcluded() {
        val privilege = Privilege(id = 1, name = "Microfone", quantity = 1)
        val meetingDate = LocalDate.of(2026, 10, 7)

        val b1 = Brother(id = 10, name = "Irmao 1", privileges = setOf(1))
        val b2 = Brother(id = 20, name = "Irmao 2", privileges = setOf(1))

        val meeting = AssignmentGenerator.generateMeeting(
            date = meetingDate,
            blocked = setOf(10), // Bloqueado pontualmente para esta reuniao
            brothers = listOf(b1, b2),
            privileges = listOf(privilege),
            history = emptyList()
        )

        assertEquals(1, meeting.assignments.size)
        assertEquals(20, meeting.assignments[0].brotherId)
    }

    @Test
    fun testFairDistributionAcrossMeetings() {
        val privilege = Privilege(id = 1, name = "Som", quantity = 1)
        val meetingDate = LocalDate.of(2026, 10, 7)

        val b1 = Brother(id = 10, name = "Carlos", privileges = setOf(1))
        val b2 = Brother(id = 20, name = "Lucas", privileges = setOf(1))

        // Carlos ja fez Som 3 vezes no histórico; Lucas fez apenas 1 vez
        val history = listOf(
            Meeting(1, "01/09/2026", "Meio de semana", listOf(Assignment(1, 10))),
            Meeting(2, "08/09/2026", "Meio de semana", listOf(Assignment(1, 10))),
            Meeting(3, "15/09/2026", "Meio de semana", listOf(Assignment(1, 10))),
            Meeting(4, "22/09/2026", "Meio de semana", listOf(Assignment(1, 20)))
        )

        val meeting = AssignmentGenerator.generateMeeting(
            date = meetingDate,
            blocked = emptySet(),
            brothers = listOf(b1, b2),
            privileges = listOf(privilege),
            history = history
        )

        assertEquals(1, meeting.assignments.size)
        assertEquals(20, meeting.assignments[0].brotherId, "Lucas deve ser priorizado por ter menos designacoes")
    }

    @Test
    fun testCsvExportAndImport() {
        val privileges = listOf(
            Privilege(1, "Som"),
            Privilege(2, "Indicador")
        )
        val brothers = listOf(
            Brother(1, "Marcos Silva", "11999998888", setOf(1, 2), true, BrotherRole.MINISTERIAL_SERVANT)
        )

        val csv = CsvDataHandler.exportBrothersToCsv(brothers, privileges)
        assertTrue(csv.contains("Marcos Silva"))
        assertTrue(csv.contains("Servo Ministerial"))

        val imported = CsvDataHandler.importBrothersFromCsv(csv)
        assertEquals(1, imported.size)
        assertEquals("Marcos Silva", imported[0].name)
        assertEquals("11999998888", imported[0].phone)
        assertEquals(BrotherRole.MINISTERIAL_SERVANT, imported[0].role)
        assertEquals(listOf("Som", "Indicador"), imported[0].privilegeNames)
    }

    @Test
    fun testWhatsAppHelper() {
        val brother = Brother(1, "Lucas Silva", "5511999998888")
        val privilege = Privilege(1, "Leitor da Sentinela")
        val meeting = Meeting(1, "11/10/2026", "Reuniao de fim de semana")

        val message = WhatsAppHelper.buildSingleMessage(
            WhatsAppHelper.DEFAULT_SINGLE_TEMPLATE,
            brother,
            privilege,
            meeting
        )

        assertTrue(message.contains("Lucas Silva"))
        assertTrue(message.contains("Leitor da Sentinela"))
        assertTrue(message.contains("11/10/2026"))
        assertTrue(message.contains("Domingo"))
    }

    @Test
    fun testFemaleExclusionFromMalePrivileges() {
        val soundPrivilege = Privilege(id = 1, name = "Som", maleOnly = true)
        val cleaningPrivilege = Privilege(id = 2, name = "Limpeza", maleOnly = false)
        val allPrivileges = listOf(soundPrivilege, cleaningPrivilege)

        val sister = Brother(
            id = 100,
            name = "Maria Oliveira",
            privileges = setOf(1, 2),
            gender = Gender.FEMALE
        )

        val brother = Brother(
            id = 101,
            name = "Joao Oliveira",
            privileges = setOf(1, 2),
            gender = Gender.MALE
        )

        // Irma nao pode receber designacao de Som (maleOnly = true)
        assertFalse(AssignmentGenerator.isBrotherAuthorizedForPrivilege(sister, soundPrivilege, allPrivileges))
        // Irmao pode receber Som
        assertTrue(AssignmentGenerator.isBrotherAuthorizedForPrivilege(brother, soundPrivilege, allPrivileges))

        // Irma e Irmao podem receber Limpeza (maleOnly = false)
        assertTrue(AssignmentGenerator.isBrotherAuthorizedForPrivilege(sister, cleaningPrivilege, allPrivileges))
        assertTrue(AssignmentGenerator.isBrotherAuthorizedForPrivilege(brother, cleaningPrivilege, allPrivileges))
    }

    @Test
    fun testPublicTalkAndCleaningWhatsAppTemplates() {
        val talk = PublicTalk(
            id = 1,
            date = "11/10/2026",
            themeNumber = 45,
            themeTitle = "Ande no caminho que conduz à vida",
            speakerName = "Carlos Souza",
            speakerCongregation = "Central",
            hospitalityNotes = "Almoco agendado"
        )
        val talkMsg = WhatsAppHelper.buildPublicTalkSpeakerMessage(
            talk = talk,
            hospitalityBrotherName = "Fernando Lima",
            hospitalityBrotherPhone = "11988887777",
            congregationName = "Jardim das Flores"
        )
        assertTrue(talkMsg.contains("Carlos Souza"))
        assertTrue(talkMsg.contains("45"))
        assertTrue(talkMsg.contains("Fernando Lima"))
        assertTrue(talkMsg.contains("Jardim das Flores"))

        val schedule = CleaningSchedule(
            id = 1,
            weekDate = "11/10/2026",
            details = "Limpeza profunda dos banheiros e auditório"
        )
        val group = FieldServiceGroup(
            id = 10,
            number = 2,
            name = "Grupo 2 (Norte)"
        )
        val cleanMsg = WhatsAppHelper.buildCleaningScheduleMessage(
            schedule = schedule,
            group = group,
            overseerName = "Roberto Silva",
            overseerPhone = "11999990000"
        )
        assertTrue(cleanMsg.contains("Grupo 2 (Norte)"))
        assertTrue(cleanMsg.contains("Roberto Silva"))
        assertTrue(cleanMsg.contains("Limpeza profunda"))
    }

    @Test
    fun testUnbaptizedBrotherEligibility() {
        val bookPrivilege = Privilege(
            id = 1, name = "Leitor do Livro",
            allowedDays = setOf(1, 2, 3, 4, 5), allowedStatus = setOf(BrotherStatus.BAPTIZED)
        )
        val sentinelPrivilege = Privilege(
            id = 2, name = "Leitor de A Sentinela",
            allowedDays = setOf(6, 7), allowedStatus = setOf(BrotherStatus.BAPTIZED)
        )
        val bibleReadingPrivilege = Privilege(id = 3, name = "Leitura da Biblia")
        val allPrivileges = listOf(bookPrivilege, sentinelPrivilege, bibleReadingPrivilege)

        val unbaptized = Brother(id = 10, name = "Irmao Nao Batizado", baptized = false, privileges = setOf(1, 2, 3))

        val weekdayMeeting = Meeting(id = 1, date = "07/10/2026", type = "Meio de semana")
        val weekendMeeting = Meeting(id = 2, date = "10/10/2026", type = "Fim de semana")

        // Nao batizado nao e candidato para Leitor do Livro (meio de semana)
        assertFalse(
            AssignmentGenerator.candidatesFor(weekdayMeeting, bookPrivilege.id, 0L, listOf(unbaptized), allPrivileges)
                .any { it.id == 10L }
        )
        // Nao batizado nao e candidato para Leitor de A Sentinela (fim de semana)
        assertFalse(
            AssignmentGenerator.candidatesFor(weekendMeeting, sentinelPrivilege.id, 0L, listOf(unbaptized), allPrivileges)
                .any { it.id == 10L }
        )
        // Nao batizado É candidato para Leitura da Biblia
        assertTrue(
            AssignmentGenerator.candidatesFor(weekdayMeeting, bibleReadingPrivilege.id, 0L, listOf(unbaptized), allPrivileges)
                .any { it.id == 10L }
        )
    }

    @Test
    fun testTraineeIsNeverAutomatic() {
        val privilege = Privilege(id = 1, name = "Indicador", quantity = 1)
        val trainee = Brother(id = 10, name = "Aprendiz", trainee = true, privileges = setOf(1))
        val regular = Brother(id = 20, name = "Regular", privileges = setOf(1))

        // generateMeeting nao escolhe aprendiz
        val meeting = AssignmentGenerator.generateMeeting(
            date = LocalDate.of(2026, 10, 7),
            blocked = emptySet(),
            brothers = listOf(trainee, regular),
            privileges = listOf(privilege),
            history = emptyList()
        )
        assertEquals(1, meeting.assignments.size)
        assertEquals(20, meeting.assignments[0].brotherId)

        // candidatesFor nao inclui aprendiz
        val meetingObj = Meeting(id = 1, date = "07/10/2026", type = "Meio de semana")
        val candidates = AssignmentGenerator.candidatesFor(meetingObj, privilege.id, 0L, listOf(trainee, regular), listOf(privilege))
        assertFalse(candidates.any { it.id == 10L })
        assertTrue(candidates.any { it.id == 20L })
    }

    @Test
    fun testTwoTraineesNotSelectedForPair() {
        val privilege = Privilege(id = 1, name = "Indicador", quantity = 2)
        val trainee1 = Brother(id = 10, name = "Aprendiz 1", trainee = true, privileges = setOf(1))
        val trainee2 = Brother(id = 11, name = "Aprendiz 2", trainee = true, privileges = setOf(1))
        val baptized1 = Brother(id = 20, name = "Batizado 1", privileges = setOf(1))
        val baptized2 = Brother(id = 21, name = "Batizado 2", privileges = setOf(1))

        val meeting = AssignmentGenerator.generateMeeting(
            date = LocalDate.of(2026, 10, 7),
            blocked = emptySet(),
            brothers = listOf(trainee1, trainee2, baptized1, baptized2),
            privileges = listOf(privilege),
            history = emptyList()
        )

        val assignedIds = meeting.assignments.map { it.brotherId }.toSet()
        assertEquals(setOf(20L, 21L), assignedIds, "O indicador deve sair com dois batizados")
    }

    @Test
    fun testTraineeAndUnbaptizedNotBothSelected() {
        val privilege = Privilege(id = 1, name = "Indicador", quantity = 2)
        val trainee = Brother(id = 10, name = "Aprendiz", trainee = true, privileges = setOf(1))
        val unbaptized = Brother(id = 11, name = "Nao Batizado", baptized = false, privileges = setOf(1))
        val baptized1 = Brother(id = 20, name = "Batizado 1", privileges = setOf(1))
        val baptized2 = Brother(id = 21, name = "Batizado 2", privileges = setOf(1))

        val meeting = AssignmentGenerator.generateMeeting(
            date = LocalDate.of(2026, 10, 7),
            blocked = emptySet(),
            brothers = listOf(trainee, unbaptized, baptized1, baptized2),
            privileges = listOf(privilege),
            history = emptyList()
        )

        val assignedIds = meeting.assignments.map { it.brotherId }.toSet()
        // Aprendiz + nao batizado nao sao os dois escolhidos juntos
        assertFalse(assignedIds.containsAll(setOf(10L, 11L)))
        assertEquals(2, assignedIds.size)
    }

    @Test
    fun testTwoUnqualifiedRuleInCandidatesFor() {
        val privilege = Privilege(id = 1, name = "Indicador", quantity = 2)
        val unbaptizedAssigned = Brother(id = 10, name = "Nao Batizado Designado", baptized = false, privileges = setOf(1))
        val baptizedAssigned = Brother(id = 20, name = "Batizado Designado", privileges = setOf(1))
        val unbaptizedCandidate = Brother(id = 11, name = "Nao Batizado Candidato", baptized = false, privileges = setOf(1))
        val baptizedCandidate = Brother(id = 21, name = "Batizado Candidato", privileges = setOf(1))

        // Reuniao com um nao batizado ja designado (id=10) e um batizado (id=20)
        val meeting = Meeting(
            id = 1,
            date = "07/10/2026",
            type = "Meio de semana",
            assignments = listOf(Assignment(1, 10), Assignment(1, 20))
        )

        // Troca manual do batizado (id=20): nao pode introduzir um segundo nao batizado
        val candidates = AssignmentGenerator.candidatesFor(
            meeting, privilege.id, 20L,
            listOf(unbaptizedAssigned, baptizedAssigned, unbaptizedCandidate, baptizedCandidate),
            listOf(privilege)
        )

        assertFalse(candidates.any { it.id == 11L }, "Nao batizado nao deve ser candidato quando ja ha um nao batizado na parte")
        assertTrue(candidates.any { it.id == 21L }, "Batizado deve ser candidato")
    }

    @Test
    fun testFutureMeetingsDoNotInfluencePreviousMeeting() {
        val schedule = MeetingSchedule(firstDay = 3, secondDay = 6)
        val privilege = Privilege(id = 1, name = "Som", quantity = 1)
        val b1 = Brother(id = 10, name = "Carlos", privileges = setOf(1))
        val b2 = Brother(id = 20, name = "Lucas", privileges = setOf(1))

        // Reuniao anterior real (30/09) com Carlos; reuniao futura (15/10) com Lucas.
        val existingMeetings = listOf(
            Meeting(1, "30/09/2026", "Meio de semana", listOf(Assignment(1, 10))),
            Meeting(2, "15/10/2026", "Meio de semana", listOf(Assignment(1, 20)))
        )

        val generated = AssignmentGenerator.generateMonth(
            yearMonth = YearMonth.of(2026, 10),
            schedule = schedule,
            brothers = listOf(b1, b2),
            privileges = listOf(privilege),
            existingMeetings = existingMeetings
        )

        // A reuniao anterior à primeira (03/10) e a de 30/09 (Carlos), nao a futura de 15/10.
        // Carlos fez a reuniao anterior → Lucas deve ser designado (evita consecutiva).
        assertEquals(20, generated.first().assignments.first().brotherId)
    }

    @Test
    fun testNextIdIsUniqueUnderConcurrency() {
        val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        val ids = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        val futures = (1..1000).map {
            executor.submit<Long> { AssignmentGenerator.nextId() }
        }
        futures.forEach { ids.add(it.get()) }
        executor.shutdown()
        assertEquals(1000, ids.size)
    }

    @Test
    fun testNormalizeNameIsLocaleStable() {
        // Acentos e cedilha sao removidos; maiúsculas viram minúsculas.
        assertEquals("joao", AssignmentGenerator.normalizeName("Joao"))
        assertEquals("acao", AssignmentGenerator.normalizeName("Acao"))
        assertEquals("coracao", AssignmentGenerator.normalizeName("CORAÇÃO"))

        // Caso turco: "I" minúsculo deve ser "i" (Locale.ROOT), nao "ı" (Locale turco).
        assertEquals("ilik", AssignmentGenerator.normalizeName("ILIK"))
        assertEquals("ırmak", AssignmentGenerator.normalizeName("ırmak"))
    }

    // ---- #36/#37: situacao teocratica do irmao (baptizado, aprendiz, leitor) ----

    private fun priv(
        id: Long,
        name: String,
        quantity: Int = 1,
        grant: ReaderGrant = ReaderGrant.NONE,
        status: Set<BrotherStatus> = BrotherStatus.entries.toSet(),
        days: Set<Int> = emptySet()
    ) = Privilege(id, name, quantity = quantity, readerGrant = grant, allowedStatus = status, allowedDays = days)

    @Test
    fun testNaoBatizadoNaoPodeLeitorDoLivroNemSentinela() {
        val livro = priv(1, "Leitor do Livro", grant = ReaderGrant.BOOK, status = setOf(BrotherStatus.BAPTIZED))
        val sentinela = priv(2, "Leitor de A Sentinela", grant = ReaderGrant.SENTINEL, status = setOf(BrotherStatus.BAPTIZED))
        val leituraDaBiblia = priv(3, "Leitura da Biblia", status = BrotherStatus.entries.toSet())
        val encenacao = priv(4, "Encenacao", status = BrotherStatus.entries.toSet())
        // O privilegio precisa estar marcado no irmao alem de a regra de status:
        // allowedStatus filtra quem PODE, nao quem esta habilitado.
        val naoBatizado = Brother(id = 10, name = "Nao Batizado", baptized = false, privileges = setOf(3L, 4L))

        listOf(livro, sentinela).forEach { p ->
            assertFalse(AssignmentGenerator.isAuthorized(naoBatizado, p), "nao batizado nao pode: ${p.name}")
        }
        // Regra do usuario: nao batizado PODE fazer a leitura da Biblia e a encenacao.
        assertTrue(AssignmentGenerator.isAuthorized(naoBatizado, leituraDaBiblia))
        assertTrue(AssignmentGenerator.isAuthorized(naoBatizado, encenacao))
    }

    @Test
    fun testReaderGrantConcedePeloDadoNaoPeloNome() {
        val livro = priv(1, "Qualquer Nome De privilegio", grant = ReaderGrant.BOOK)
        val sentinela = priv(2, "Outro Nome Qualquer", grant = ReaderGrant.SENTINEL)
        val leitor = Brother(id = 10, name = "Leitor", isReader = true)
        val sentineleiro = Brother(id = 11, name = "Sentineleiro", isSentinelReader = true)
        val semNada = Brother(id = 12, name = "Sem Flags")

        assertTrue(AssignmentGenerator.isAuthorized(leitor, livro))
        assertFalse(AssignmentGenerator.isAuthorized(leitor, sentinela))
        assertTrue(AssignmentGenerator.isAuthorized(sentineleiro, sentinela))
        // Heranca: leitor de A Sentinela tambem pode ler o livro.
        assertTrue(AssignmentGenerator.isAuthorized(sentineleiro, livro))
        // Privilegio sem readerGrant nao e concedido por flag de leitor.
        assertFalse(AssignmentGenerator.isAuthorized(semNada, priv(3, "Som")))
    }

    @Test
    fun testAprendizNuncaEhCandidatoAutomatico() {
        val privLeitura = priv(1, "Leitura")
        val aprendiz = Brother(id = 10, name = "Aprendiz", trainee = true)
        assertTrue(AssignmentGenerator.candidatesFor(
            Meeting(id = 1, date = "07/10/2026", type = "Meio de semana"),
            1, 0, listOf(aprendiz, Brother(id = 11, name = "Qualificado")), listOf(privLeitura)
        ).none { it.id == aprendiz.id })
    }

    @Test
    fun testNoMaximoUmNaoQualificadoPorParteComDuasPessoas() {
        // Indicador: dois, e exige batizado.
        val indicador = priv(1, "Indicacao", quantity = 2, status = setOf(BrotherStatus.BAPTIZED))
        val data = LocalDate.of(2026, 10, 7)
        val history = listOf(
            Meeting(id = 90, date = "29/09/2026", type = "Meio de semana")
        )
        val lista = listOf(
            Brother(id = 1, name = "Batizado A", privileges = setOf(1L)),
            Brother(id = 2, name = "Batizado B", privileges = setOf(1L)),
            Brother(id = 3, name = "Aprendiz", trainee = true, privileges = setOf(1L))
        )
        val meeting = AssignmentGenerator.generateMeeting(
            date = data, blocked = emptySet(), brothers = lista,
            privileges = listOf(indicador), history = history
        )
        val designados = meeting.assignments.filter { it.privilegeId == indicador.id }
            .map { lista.first { b -> b.id == it.brotherId } }

        // Aprendiz nunca entra sozinho na geracao automatica.
        assertTrue(designados.none { it.trainee }, "aprendiz nao pode ser automatico")
        assertEquals(2, designados.size)
        assertTrue(designados.all { it.baptized })
    }

    @Test
    fun testDoisNaoQualificadosNaoDividemAParteDeDois() {
        val indicador = priv(1, "Indicacao", quantity = 2)
        // Parte com 2: um aprendiz (1) e um batizado (3). Trocar o batizado (3)
        // por um nao batizado (2) deixaria DOIS nao qualificados na mesma parte.
        val meeting = Meeting(
            id = 1, date = "07/10/2026", type = "Meio de semana",
            assignments = listOf(
                Assignment(privilegeId = 1, brotherId = 1),
                Assignment(privilegeId = 1, brotherId = 3)
            )
        )
        val lista = listOf(
            Brother(id = 1, name = "Aprendiz", trainee = true, privileges = setOf(1L)),
            Brother(id = 2, name = "Nao Batizado", baptized = false, privileges = setOf(1L)),
            Brother(id = 3, name = "Batizado A", privileges = setOf(1L)),
            Brother(id = 4, name = "Batizado B", privileges = setOf(1L))
        )
        val candidatos = AssignmentGenerator.candidatesFor(
            meeting, privilegeId = 1, currentBrotherId = 3,
            brothers = lista, privileges = listOf(indicador)
        ).map { it.id }.toSet()

        // Regra do usuario: dois nao qualificados nao dividem a mesma parte.
        assertTrue(2 !in candidatos, "nao batizado nao pode entrar ao lado de um aprendiz")
        assertTrue(4 in candidatos, "batizado qualificado pode entrar")
    }

    @Test
    fun testReaderGrantSobreviveAoNomeDoPrivilegio() {
        // Regressao: a regra nao pode depender do texto. Renomear o privilegio
        // nao pode alterar quem pode faze-lo.
        val antes = priv(1, "Leitor do livro", grant = ReaderGrant.BOOK)
        val depois = antes.copy(name = "Leitura do livro")
        val leitor = Brother(id = 10, name = "Leitor", isReader = true)
        assertEquals(
            AssignmentGenerator.isAuthorized(leitor, antes),
            AssignmentGenerator.isAuthorized(leitor, depois)
        )
    }

    // ---- #20: regras de negocio puras sem cobertura ----

    @Test
    fun `privilegio do dia com designacao a menos que a quantidade fica pendente`() {
        val indicador = Privilege(id = 1, name = "Indicador", quantity = 2, allowedDays = setOf(3))
        val hoje = Meeting(1, "07/10/2026", "Meio de semana", listOf(Assignment(1, 10)))

        val pendentes = AssignmentGenerator.missingAssignments(hoje, listOf(indicador))

        // 1 de 2 designados: ainda falta um.
        assertEquals(listOf(1L), pendentes.map { it.id })
    }

    @Test
    fun `privilegio so entra como pendencia no dia configurado`() {
        val soQuarta = Privilege(id = 1, name = "Som", quantity = 1, allowedDays = setOf(3))
        val desativado = Privilege(id = 2, name = "Desativado", quantity = 1, active = false)
        val mercoledi = Meeting(1, "07/10/2026", "Meio de semana", emptyList())

        // allowedDays = {3} significa "so na quarta". Numa quarta o privilegio
        // se aplica e esta sem designar, entao entra como pendencia.
        assertEquals(
            listOf("Som"),
            AssignmentGenerator.missingAssignments(mercoledi, listOf(soQuarta, desativado)).map { it.name },
            "na quarta, Som se aplica e falta; o desativado nunca entra"
        )

        // No sabado (6) o privilegio nao se aplica, mesmo sem designacao.
        val sabado = Meeting(2, "10/10/2026", "Fim de semana", emptyList())
        assertTrue(
            AssignmentGenerator.missingAssignments(sabado, listOf(soQuarta, desativado)).isEmpty(),
            "no sabado Som nao se aplica e o desativado e ignorado"
        )
    }

    @Test
    fun `periodo de indisponibilidade e inclusivo nos dois extremos`() {
        val irmao = Brother(
            id = 10, name = "Viajante",
            unavailabilities = listOf(
                UnavailablePeriod(id = 1, startDate = "01/10/2026", endDate = "15/10/2026", reason = "Férias")
            )
        )

        assertTrue(AssignmentGenerator.isBrotherUnavailableOn(irmao, LocalDate.of(2026, 10, 1)))
        assertTrue(AssignmentGenerator.isBrotherUnavailableOn(irmao, LocalDate.of(2026, 10, 15)))
        assertFalse(AssignmentGenerator.isBrotherUnavailableOn(irmao, LocalDate.of(2026, 9, 30)))
        assertFalse(AssignmentGenerator.isBrotherUnavailableOn(irmao, LocalDate.of(2026, 10, 16)))
    }

    @Test
    fun `periodo com data invalida nao bloqueia o irmao`() {
        // Data ilegível não pode tirar o irmão da escala.
        val irmao = Brother(
            id = 10, name = "Data Ruim",
            unavailabilities = listOf(
                UnavailablePeriod(id = 1, startDate = "01/10/2026", endDate = "", reason = "Data inválida")
            )
        )
        assertFalse(AssignmentGenerator.isBrotherUnavailableOn(irmao, LocalDate.of(2026, 10, 7)))
    }

    @Test
    fun `telefone e normalizado para digitos no link do WhatsApp`() {
        val link = WhatsAppHelper.buildWebLink("+55 (11) 99999-8888", "Olá, Tudo bem?")

        assertTrue(link.startsWith("https://web.whatsapp.com/send?phone=5511999998888&text="), link)
        // URLEncoder é o padrão de application/x-www-form-urlencoded: espaço
        // vira '+'. %20 também é aceito pelo WhatsApp; exigir só %20 testava
        // uma suposição errada, não um defeito.
        assertTrue(
            link.contains("Ol%C3%A1%2C+Tudo+bem%3F") || link.contains("Ol%C3%A1%2C%20Tudo%20bem%3F"),
            link
        )
        // Sem telefone o link ainda funciona, só sem o parâmetro phone.
        assertFalse(WhatsAppHelper.buildWebLink("", "oi").contains("phone="))
    }

    @Test
    fun `broadcast lista designacoes e pendencias`() {
        val meeting = Meeting(
            id = 1, date = "07/10/2026", type = "Reunião do meio de semana",
            assignments = listOf(Assignment(1, 10))
        )
        val irmaos = listOf(Brother(10, "Joao"))
        val privs = listOf(Privilege(1, "Som", quantity = 1), Privilege(2, "Indicador", quantity = 2))

        val msg = WhatsAppHelper.buildMeetingBroadcastMessage(
            null, meeting, irmaos, privs,
            missingPrivileges = listOf(privs[1])
        )

        assertTrue(msg.contains("Som"), msg)
        assertTrue(msg.contains("Joao"), msg)
        assertTrue(msg.contains("Pendências:"), msg)
        assertTrue(msg.contains("Indicador"), msg)
        assertTrue(msg.contains("Quarta"), msg)
    }
}
