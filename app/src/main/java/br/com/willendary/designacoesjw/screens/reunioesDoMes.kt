package br.com.willendary.designacoesjw.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.willendary.designacoesjw.data.*
import br.com.willendary.designacoesjw.data.BuscaHistorico
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.export.ImageExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import br.com.willendary.designacoesjw.notification.MeetingReminderHelper
import br.com.willendary.designacoesjw.screens.EditBrotherDialog
import br.com.willendary.designacoesjw.screens.EditPrivilegeDialog
import br.com.willendary.designacoesjw.stats.EquityStatisticsHelper
import br.com.willendary.designacoesjw.ui.MeetingProgramList
import br.com.willendary.designacoesjw.export.MonthBoardPrint
import br.com.willendary.designacoesjw.sync.Changelog
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.sync.EstadoSincronizacao
import br.com.willendary.designacoesjw.ui.mostrarDesfazivel
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwSectionLabel
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.ui.corDeContorno
import br.com.willendary.designacoesjw.ui.MonthBoard
import br.com.willendary.designacoesjw.util.Datas
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import br.com.willendary.designacoesjw.*


/** Mesma conta de mês do `PrintReportScreen`: a data é `dd/MM/yyyy`. */
fun reunioesDoMes(meetings: List<Meeting>, mes: YearMonth): List<Meeting> {
    val prefixo = mes.format(DateTimeFormatter.ofPattern("MM/yyyy"))
    return meetings.filter { it.date.endsWith("/$prefixo") }
        .sortedBy { AssignmentGenerator.parseDate(it.date) }
}
