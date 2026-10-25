package br.com.willendary.designacoesjw.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.generator.MeetingPlanner
import br.com.willendary.designacoesjw.ui.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Testa os componentes reais e gera PNGs inspecionáveis como artefatos do CI. */
class AgendaUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var previousHome: String
    private lateinit var testHome: File
    private val people = listOf(Brother(1, "João Silva"), Brother(2, "João Silva"))
    private val meeting = Meeting(10, "28/10/2026", MeetingType.MIDWEEK.label,
        theme = "PROVÉRBIOS 10–12", program = listOf(
            ProgramItem("Faça seu melhor no ministério", 1, "Iniciando conversas", 3, PartKind.PAIR)))

    @Before fun isolateStorage() {
        previousHome = System.getProperty("user.home")
        testHome = Files.createTempDirectory("designacoes-ui-").toFile()
        System.setProperty("user.home", testHome.absolutePath)
    }

    @After fun restoreStorage() {
        System.setProperty("user.home", previousHome)
        testHome.deleteRecursively()
    }

    @Test fun homonymsCanBeSelectedAndRemovedIndependently() {
        var current by mutableStateOf(meeting)
        rule.setContent {
            JwThemeProvider(isDark = false) {
                Surface(Modifier.width(390.dp).testTag("preview")) {
                    MeetingProgramList(current, people, emptyList(), canAssign = people,
                        onToggleAssignment = { position, id -> current = MeetingPlanner.toggleProgramAssignment(current, position, id) })
                }
            }
        }
        rule.onNodeWithContentDescription("Editar participantes").performClick()
        rule.onNodeWithText("Buscar participante").performTextInput("joao")
        rule.onAllNodes(isToggleable())[1].performClick()
        rule.runOnIdle { assertEquals(listOf(2L), current.programAssignments.single().brotherIds) }
        snapshot("seletor-homonimos", "preview")
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.runOnIdle { assertTrue(current.programAssignments.isEmpty()) }
    }

    @Test fun absentParticipantCannotBeSelected() {
        val unavailable = people[0].copy(unavailabilities = listOf(UnavailablePeriod(1, meeting.date, meeting.date)))
        rule.setContent {
            JwThemeProvider {
                Surface(Modifier.width(390.dp).testTag("preview")) {
                    MeetingProgramList(meeting, listOf(unavailable, people[1]), emptyList(), canAssign = listOf(unavailable, people[1]))
                }
            }
        }
        rule.onNodeWithContentDescription("Editar participantes").performClick()
        rule.onAllNodes(isToggleable())[0].assertIsNotEnabled()
        rule.onAllNodes(isToggleable())[1].assertIsEnabled()
    }

    @Test fun removedParticipantCanBeClearedWhenNoCandidatesRemain() {
        var current by mutableStateOf(meeting.copy(programAssignments = listOf(ProgramAssignment(1, listOf(99)))))
        rule.setContent {
            JwThemeProvider {
                Surface(Modifier.width(390.dp).testTag("preview")) {
                    MeetingProgramList(current, emptyList(), emptyList(), canEdit = true,
                        onToggleAssignment = { position, id -> current = MeetingPlanner.toggleProgramAssignment(current, position, id) })
                }
            }
        }
        rule.onNodeWithContentDescription("Editar participantes").performClick()
        rule.onNode(isToggleable()).performClick()
        rule.runOnIdle { assertTrue(current.programAssignments.isEmpty()) }
    }

    @Test fun narrowLightTheme() = renderProgram(false, "programa-claro")
    @Test fun narrowDarkTheme() = renderProgram(true, "programa-escuro")

    private fun renderProgram(dark: Boolean, name: String) {
        rule.setContent {
            JwThemeProvider(isDark = dark) {
                Surface(Modifier.width(390.dp).testTag("preview"), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AgendaOverview(listOf(MeetingPlanner.review(meeting, people, emptyList())), people.size)
                        MeetingProgramList(meeting, people, emptyList(), canAssign = people)
                    }
                }
            }
        }
        rule.onNodeWithText("A designar").assertIsDisplayed()
        snapshot(name, "preview")
    }

    @Test fun desktopAgendaFiltersAndCompletesWithoutLosingProgram() {
        val today = LocalDate.now()
        val current = meeting.copy(date = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        val data = Store(brothers = people, meetings = listOf(current))
        File(testHome, ".designacoes-jw/dados.json").apply { parentFile.mkdirs(); writeText(Json.encodeToString(data)) }
        val controller = StoreController()
        rule.setContent { JwThemeProvider { Surface { DesktopApp(controller) } } }
        rule.onNodeWithText("Buscar pessoa, parte ou data").performScrollTo().performTextInput("inexistente")
        rule.onNodeWithText("Nenhuma reunião corresponde aos filtros.").assertExists()
        rule.onNodeWithText("Buscar pessoa, parte ou data").performTextClearance()
        rule.onNodeWithText("Completar escala").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(current.program, controller.data.meetings.single { it.id == current.id }.program) }
        snapshot("agenda-desktop")
        rule.onNodeWithText("Buscar pessoa, parte ou data").performScrollTo().performTextInput("Iniciando")
        rule.onNodeWithText("Ver detalhes").performScrollTo().performClick()
        rule.onNodeWithText("Iniciando conversas").performScrollTo().assertIsDisplayed()
        snapshot("agenda-programa-desktop")
    }

    private fun snapshot(name: String, tag: String? = null) {
        rule.waitForIdle()
        val image = (if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)).captureToImage()
        val pixels = image.toPixelMap()
        val bitmap = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) bitmap.setRGB(x, y, pixels[x, y].toArgb())
        File("build/ui-screenshots/$name.png").apply { parentFile.mkdirs(); ImageIO.write(bitmap, "png", this) }
    }
}
