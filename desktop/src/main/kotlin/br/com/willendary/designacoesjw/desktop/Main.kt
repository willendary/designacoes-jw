package br.com.willendary.designacoesjw.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random

@Serializable data class Brother(val id:Long,val name:String,val phone:String="",val privileges:Set<Long> = emptySet(),val active:Boolean=true)
@Serializable data class Privilege(val id:Long,val name:String,val quantity:Int=1,val active:Boolean=true,val allowedDays:Set<Int> = emptySet())
@Serializable data class Assignment(val privilegeId:Long,val brotherId:Long)
@Serializable data class Meeting(val id:Long,val date:String,val type:String,val assignments:List<Assignment> = emptyList())
@Serializable data class Store(val brothers:List<Brother> = emptyList(),val privileges:List<Privilege> = emptyList(),val meetings:List<Meeting> = emptyList(),val firstDay:Int=3,val secondDay:Int=6)

private val json=Json{prettyPrint=true;ignoreUnknownKeys=true}
private val fmt=DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val days=listOf(
    DayOfWeek.MONDAY to "Segunda-feira",DayOfWeek.TUESDAY to "Terça-feira",
    DayOfWeek.WEDNESDAY to "Quarta-feira",DayOfWeek.THURSDAY to "Quinta-feira",
    DayOfWeek.FRIDAY to "Sexta-feira",DayOfWeek.SATURDAY to "Sábado",DayOfWeek.SUNDAY to "Domingo"
)
private fun norm(s:String)=Normalizer.normalize(s.trim(),Normalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(),"").lowercase(Locale.getDefault())
private fun newId()=System.currentTimeMillis()*1000L+Random.nextLong(1000)
private fun dayName(v:Int)=days.firstOrNull{it.first.value==v}?.second ?: "—"

class StoreController {
    private val file=File(System.getProperty("user.home"),".designacoes-jw/dados.json")
    var data by mutableStateOf(load())
        private set
    private fun load()=runCatching{if(file.exists())json.decodeFromString<Store>(file.readText()) else Store()}.getOrDefault(Store())
    private fun save(s:Store){data=s;file.parentFile.mkdirs();file.writeText(json.encodeToString(s))}
    fun addBrother(name:String,phone:String){
        if(name.isBlank()||data.brothers.any{norm(it.name)==norm(name)})return
        save(data.copy(brothers=(data.brothers+Brother(newId(),name.trim(),phone.trim())).sortedBy{norm(it.name)}))
    }
    fun deleteBrother(id:Long)=save(data.copy(brothers=data.brothers.filterNot{it.id==id}))
    fun addPrivilege(name:String,q:Int){
        if(name.isBlank()||data.privileges.any{norm(it.name)==norm(name)})return
        save(data.copy(privileges=(data.privileges+Privilege(newId(),name.trim(),q.coerceAtLeast(1))).sortedBy{norm(it.name)}))
    }
    fun toggle(bid:Long,pid:Long){
        save(data.copy(brothers=data.brothers.map{b->if(b.id!=bid)b else b.copy(privileges=b.privileges.toMutableSet().also{x->if(!x.add(pid))x.remove(pid)})}))
    }
    fun days(a:Int,b:Int){if(a!=b)save(data.copy(firstDay=a,secondDay=b))}
    fun generate(month:YearMonth){
        val dates=(1..month.lengthOfMonth()).map{month.atDay(it)}.filter{it.dayOfWeek.value==data.firstDay||it.dayOfWeek.value==data.secondDay}
        val keep=data.meetings.filterNot{runCatching{YearMonth.from(LocalDate.parse(it.date,fmt))==month}.getOrDefault(false)}
        val generated=dates.map{generateMeeting(it,keep)}
        save(data.copy(meetings=(keep+generated).sortedBy{it.date}))
    }
    private fun authorized(b:Brother,p:Privilege):Boolean{
        if(p.id in b.privileges)return true
        val book=data.privileges.firstOrNull{norm(it.name) in setOf("leitor do livro","leitor livro")}
        val sent=data.privileges.firstOrNull{norm(it.name) in setOf("leitor da sentinela","leitor sentinela")}
        return book?.id==p.id && sent?.id in b.privileges
    }
    private fun generateMeeting(date:LocalDate,history:List<Meeting>):Meeting{
        val used=mutableSetOf<Long>();val result=mutableListOf<Assignment>()
        data.privileges.filter{it.active&&(it.allowedDays.isEmpty()||date.dayOfWeek.value in it.allowedDays)}.sortedBy{norm(it.name)}.forEach{p->
            val candidates=data.brothers.filter{it.active&&it.id !in used&&authorized(it,p)}
                .sortedWith(compareBy<Brother>{b->history.count{m->m.assignments.any{a->a.brotherId==b.id&&a.privilegeId==p.id}}}.thenBy{norm(it.name)})
            candidates.take(p.quantity).forEach{b->result+=Assignment(p.id,b.id);used+=b.id}
        }
        val type=if(date.dayOfWeek.value==6)"Reunião de fim de semana" else "Reunião do meio de semana"
        return Meeting(newId(),date.format(fmt),type,result)
    }
}

fun main()=application{
    val c=remember{StoreController()}
    var updateInfo by remember { mutableStateOf<WindowsUpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        updateInfo = WindowsUpdateManager.checkForUpdate()
        checkingUpdate = false
    }

    Window(onCloseRequest=::exitApplication,title="Designações JW 0.1.6",state=rememberWindowState(width=1200.dp,height=760.dp)){
        MaterialTheme{
            DesktopApp(c)
            if (!checkingUpdate && updateInfo != null) {
                UpdateDialog(
                    info = updateInfo!!,
                    onUpdate = {
                        WindowsUpdateManager.downloadAndInstall(updateInfo!!)
                    },
                    onDismiss = { updateInfo = null }
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    info: WindowsUpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova versão disponível") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Uma nova versão do Designações JW está disponível.")
                Text("Instalada: 0.1.6")
                Text("Nova versão: ${info.version}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("O aplicativo será fechado e reaberto automaticamente durante a atualização.")
            }
        },
        confirmButton = {
            Button(onClick = onUpdate) {
                Text("Atualizar agora")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Depois")
            }
        }
    )
}

@Composable fun DesktopApp(c:StoreController){
    var tab by remember{mutableStateOf(0)}
    val labels=listOf("Início","Irmãos","Privilégios","Histórico","Configurações")
    val icons=listOf(Icons.Default.Home,Icons.Default.Groups,Icons.Default.Work,Icons.Default.History,Icons.Default.Settings)
    Row(Modifier.fillMaxSize()){
        NavigationRail{
            Text("DJW",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(16.dp))
            labels.forEachIndexed{i,label->NavigationRailItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label)})}
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().padding(24.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Icon(Icons.Default.CalendarMonth,null,tint=MaterialTheme.colorScheme.primary)
                Text("Designações JW",style=MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(20.dp))
            when(tab){0->Home(c);1->Brothers(c);2->Privileges(c);3->History(c);4->Settings(c)}
        }
    }
}

@Composable private fun Home(c:StoreController){
    var month by remember{mutableStateOf(YearMonth.now())}
    val meetings=c.data.meetings.filter{runCatching{YearMonth.from(LocalDate.parse(it.date,fmt))==month}.getOrDefault(false)}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            FilledTonalButton({month=month.minusMonths(1)}){Text("‹")}
            Text(month.month.getDisplayName(TextStyle.FULL,Locale("pt","BR")).replaceFirstChar{it.uppercase()}+" "+month.year,style=MaterialTheme.typography.titleLarge)
            FilledTonalButton({month=month.plusMonths(1)}){Text("›")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Stat("Irmãos",c.data.brothers.count{it.active});Stat("Privilégios",c.data.privileges.count{it.active});Stat("Reuniões",meetings.size)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
            Button({c.generate(month)}){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(6.dp));Text("Gerar designações")}
            Text("Reuniões: "+dayName(c.data.firstDay)+" e "+dayName(c.data.secondDay))
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){
            items(meetings){m->Card(Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Column{Text(m.date,style=MaterialTheme.typography.titleMedium);Text(m.type,color=MaterialTheme.colorScheme.primary);Text(m.assignments.size.toString()+" designação(ões)")}
                Column(horizontalAlignment=Alignment.End){m.assignments.forEach{a->Text((c.data.privileges.find{it.id==a.privilegeId}?.name?:"Privilégio")+"  —  "+(c.data.brothers.find{it.id==a.brotherId}?.name?:"Irmão"))}}
            }}}
        }
    }
}
@Composable private fun Stat(t:String,v:Int){Card(Modifier.width(180.dp)){Column(Modifier.padding(16.dp)){Text(v.toString(),style=MaterialTheme.typography.headlineMedium);Text(t)}}}

@Composable private fun Brothers(c:StoreController){
    var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var search by remember{mutableStateOf("")}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Irmãos",style=MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Nome")},modifier=Modifier.weight(1f));OutlinedTextField(phone,{phone=it},label={Text("WhatsApp")},modifier=Modifier.weight(1f));Button({c.addBrother(name,phone);name="";phone=""}){Text("Adicionar")}}
        OutlinedTextField(search,{search=it},label={Text("Buscar irmão")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth())
        LazyColumn{items(c.data.brothers.filter{norm(it.name).contains(norm(search))}){b->ListItem(headlineContent={Text(b.name)},supportingContent={Text(if(b.phone.isBlank())"Sem WhatsApp" else b.phone)},trailingContent={IconButton({c.deleteBrother(b.id)}){Icon(Icons.Default.Delete,"Excluir")}})}}
    }
}

@Composable private fun Privileges(c:StoreController){
    var name by remember{mutableStateOf("")};var q by remember{mutableStateOf("1")}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Privilégios",style=MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Nome")},modifier=Modifier.weight(1f));OutlinedTextField(q,{q=it.filter(Char::isDigit)},label={Text("Quantidade")},modifier=Modifier.width(130.dp));Button({c.addPrivilege(name,q.toIntOrNull()?:1);name="";q="1"}){Text("Adicionar")}}
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(c.data.privileges){p->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
            Text(p.name,style=MaterialTheme.typography.titleMedium);Text("Quantidade: "+p.quantity)
            if(norm(p.name) in setOf("leitor da sentinela","leitor sentinela"))Text("Leitor da Sentinela → também pode ler o Livro",color=MaterialTheme.colorScheme.primary)
            c.data.brothers.forEach{b->
                val inherited=norm(p.name) in setOf("leitor do livro","leitor livro")&&c.data.privileges.any{s->norm(s.name) in setOf("leitor da sentinela","leitor sentinela")&&s.id in b.privileges}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(p.id in b.privileges||inherited,{if(!inherited)c.toggle(b.id,p.id)},enabled=!inherited);Text(b.name);if(inherited)Text("  (Sentinela)",color=MaterialTheme.colorScheme.primary)}
            }
        }}}}
    }
}

@Composable private fun History(c:StoreController){
    val groups=c.data.meetings.sortedByDescending{it.date}.groupBy{it.date.substringAfterLast("/")}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){groups.forEach{(month,items)->item{Text(month,style=MaterialTheme.typography.titleMedium)};items(items){m->Card(Modifier.fillMaxWidth()){ListItem(headlineContent={Text(m.date)},supportingContent={Text(m.type+" • "+m.assignments.size+" designação(ões)")})}}}}
}
@Composable private fun Settings(c:StoreController){
    var first by remember{mutableStateOf(c.data.firstDay)};var second by remember{mutableStateOf(c.data.secondDay)}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("Configurações",style=MaterialTheme.typography.headlineSmall);Text("Dias de reunião")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Selector("Primeiro dia",first){first=it;c.days(first,second)};Selector("Segundo dia",second){second=it;c.days(first,second)}}
        Text("Os dados do Windows ficam armazenados localmente em:",style=MaterialTheme.typography.labelLarge)
        Text(File(System.getProperty("user.home"),".designacoes-jw/dados.json").absolutePath,style=MaterialTheme.typography.bodySmall)
        Text("Designações JW 0.1.6",style=MaterialTheme.typography.labelLarge)
    }
}
@Composable private fun Selector(title:String,value:Int,onChange:(Int)->Unit){
    var open by remember{mutableStateOf(false)}
    Box{OutlinedButton({open=true}){Text(title+": "+dayName(value))};DropdownMenu(open,{open=false}){days.forEach{(d,l)->DropdownMenuItem(text={Text(l)},onClick={onChange(d.value);open=false})}}}
}
