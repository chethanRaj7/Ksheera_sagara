@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ksheerasagara

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = KsheeraDatabase.getDatabase(applicationContext)

        setContent {
            KsheeraTheme {
                KsheeraApp(database.dao())
            }
        }
    }
}

@Entity(tableName = "cows")
data class CowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val breed: String
)

@Entity(tableName = "milk_entries")
data class MilkEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val cowId: Long?,
    val liters: Double,
    val fat: Double,
    val snf: Double,
    val amount: Double
)

@Entity(tableName = "expense_entries")
data class ExpenseEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val cowId: Long?,
    val category: String,
    val amount: Double,
    val note: String
)

@Dao
interface DairyDao {
    @Query("SELECT * FROM cows ORDER BY name COLLATE NOCASE")
    fun observeCows(): Flow<List<CowEntity>>

    @Query("SELECT * FROM milk_entries WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeMilkEntries(start: Long, end: Long): Flow<List<MilkEntryEntity>>

    @Query("SELECT * FROM expense_entries WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeExpenseEntries(start: Long, end: Long): Flow<List<ExpenseEntryEntity>>

    @Insert
    suspend fun insertCow(cow: CowEntity)

    @Insert
    suspend fun insertMilkEntry(entry: MilkEntryEntity)

    @Insert
    suspend fun insertExpenseEntry(entry: ExpenseEntryEntity)
}

@Database(
    entities = [CowEntity::class, MilkEntryEntity::class, ExpenseEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KsheeraDatabase : RoomDatabase() {
    abstract fun dao(): DairyDao

    companion object {
        @Volatile private var instance: KsheeraDatabase? = null

        fun getDatabase(context: Context): KsheeraDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KsheeraDatabase::class.java,
                    "ksheera_sagara.db"
                ).build().also { instance = it }
            }
        }
    }
}

private enum class Tab(val label: String) {
    Dashboard("Dashboard"),
    Income("Income"),
    Expense("Expense"),
    Cows("Cows")
}

private data class MonthCursor(val year: Int, val month: Int)

private data class MonthlySummary(
    val income: Double,
    val expenses: Double,
    val liters: Double,
    val netProfit: Double,
    val profitPerLiter: Double,
    val expensesByCategory: Map<String, Double>,
    val recentEntries: List<RecentEntry>,
    val cowProfits: List<CowProfit>
)

private data class RecentEntry(
    val date: Long,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val income: Boolean
)

private data class CowProfit(
    val cow: CowEntity,
    val liters: Double,
    val income: Double,
    val expenses: Double,
    val profit: Double
)

private val ExpenseCategories = listOf("Fodder", "Medical", "Labor", "Electricity", "Other")
private val DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

@Composable
private fun KsheeraTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF63D8A5),
            onPrimary = Color(0xFF092017),
            background = Color(0xFF0A120E),
            onBackground = Color(0xFFEAF4ED),
            surface = Color(0xFF111C16),
            onSurface = Color(0xFFEAF4ED),
            surfaceVariant = Color(0xFF1A2A22),
            onSurfaceVariant = Color(0xFFB8C7BE),
            error = Color(0xFFFF7D86)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0F9E68),
            onPrimary = Color.White,
            background = Color(0xFFF4F8F1),
            onBackground = Color(0xFF112018),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF112018),
            surfaceVariant = Color(0xFFE1EEE6),
            onSurfaceVariant = Color(0xFF53645B),
            error = Color(0xFFB42332)
        )
    }

    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun KsheeraApp(dao: DairyDao) {
    var selectedTab by remember { mutableStateOf(Tab.Dashboard) }
    var month by remember { mutableStateOf(currentMonth()) }
    val (start, end) = remember(month) { monthBounds(month) }
    val cows by dao.observeCows().collectAsState(initial = emptyList())
    val milkEntries by remember(start, end) { dao.observeMilkEntries(start, end) }
        .collectAsState(initial = emptyList())
    val expenseEntries by remember(start, end) { dao.observeExpenseEntries(start, end) }
        .collectAsState(initial = emptyList())
    val summary = remember(cows, milkEntries, expenseEntries) {
        buildMonthlySummary(cows, milkEntries, expenseEntries)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header(month, onPrevious = { month = month.shift(-1) }, onNext = { month = month.shift(1) })
            TabBar(selectedTab) { selectedTab = it }
            Crossfade(targetState = selectedTab, animationSpec = tween(180), label = "tab-crossfade") { tab ->
                when (tab) {
                    Tab.Dashboard -> DashboardScreen(summary, month)
                    Tab.Income -> IncomeScreen(dao, cows)
                    Tab.Expense -> ExpenseScreen(dao, cows)
                    Tab.Cows -> CowsScreen(dao, summary.cowProfits)
                }
            }
        }
    }
}

@Composable
private fun Header(month: MonthCursor, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text(
                text = "Ksheera-Sagara",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Dairy Profit/Loss Calculator",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavButton("<", onPrevious)
            Text(
                text = month.title(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            NavButton(">", onNext)
        }
    }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(58.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    }
}

@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Tab.entries.forEach { tab ->
            val active = selected == tab
            Button(
                onClick = { onSelect(tab) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.width(112.dp)
            ) {
                Text(
                    text = tab.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(summary: MonthlySummary, month: MonthCursor) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FinancialHealthCard(summary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { sharePdf(context, month.title(), summary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Share PDF", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { shareImage(context, month.title(), summary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Share Image", fontWeight = FontWeight.Bold)
            }
        }
        ExpenseMixCard(summary)
        CoachCard(summary)
        RecentEntriesCard(summary.recentEntries)
    }
}

@Composable
private fun FinancialHealthCard(summary: MonthlySummary) {
    val healthy = summary.netProfit >= 0.0
    val healthColor by animateColorAsState(
        targetValue = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        animationSpec = tween(500),
        label = "health-color"
    )

    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Financial Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(healthColor.copy(alpha = 0.22f))
                    .padding(horizontal = 28.dp, vertical = 9.dp)
            ) {
                Text(if (healthy) "Green" else "Red", color = healthColor, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = money(summary.netProfit),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = healthColor
        )
        Text("Net profit this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Income", money(summary.income), MaterialTheme.colorScheme.primary)
            Metric("Expenses", money(summary.expenses), MaterialTheme.colorScheme.error)
            Metric("Per liter", money(summary.profitPerLiter), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun ExpenseMixCard(summary: MonthlySummary) {
    Panel {
        Text("Expense Mix", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(summary.expensesByCategory)
        }
        Spacer(Modifier.height(10.dp))
        ExpenseCategories.forEachIndexed { index, category ->
            val amount = summary.expensesByCategory[category].orZero()
            if (amount > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(chartColors[index % chartColors.size])
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(category)
                    }
                    Text(money(amount), fontWeight = FontWeight.Bold)
                }
            }
        }
        if (summary.expenses <= 0.0) {
            Text("No expense entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DonutChart(expensesByCategory: Map<String, Double>) {
    val total = expensesByCategory.values.sum()
    Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 32.dp.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(size.width - 42.dp.toPx(), size.height - 42.dp.toPx())
            val topLeft = androidx.compose.ui.geometry.Offset(21.dp.toPx(), 21.dp.toPx())
            if (total <= 0.0) {
                drawArc(
                    color = Color.Gray.copy(alpha = 0.22f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            } else {
                var start = -90f
                ExpenseCategories.forEachIndexed { index, category ->
                    val amount = expensesByCategory[category].orZero()
                    if (amount > 0.0) {
                        val sweep = (amount / total * 360.0).toFloat()
                        drawArc(
                            color = chartColors[index % chartColors.size],
                            startAngle = start,
                            sweepAngle = max(1f, sweep - 2f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                        start += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Expenses", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(money(total), fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun CoachCard(summary: MonthlySummary) {
    val coachLines = coachAdvice(summary)
    Panel {
        Text("AI Profit Coach", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        coachLines.forEachIndexed { index, line ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Text(line, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun RecentEntriesCard(entries: List<RecentEntry>) {
    Panel {
        Text("Recent Entries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Text("Add a milk slip or expense to start the month ledger.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            entries.take(6).forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${shortDate(entry.date)} ${entry.title}", fontWeight = FontWeight.Bold)
                        if (entry.subtitle.isNotBlank()) {
                            Text(entry.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = money(if (entry.income) entry.amount else -entry.amount),
                        color = if (entry.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomeScreen(dao: DairyDao, cows: List<CowEntity>) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(DateFormat.format(Date())) }
    var liters by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var snf by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cowId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    Panel {
        Text("Income Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        MoneyTextField("Date", date, { date = it }, KeyboardType.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MoneyTextField("Liters", liters, { liters = it }, KeyboardType.Decimal, Modifier.weight(1f))
            MoneyTextField("Fat %", fat, { fat = it }, KeyboardType.Decimal, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MoneyTextField("SNF %", snf, { snf = it }, KeyboardType.Decimal, Modifier.weight(1f))
            MoneyTextField("Slip amount", amount, { amount = it }, KeyboardType.Decimal, Modifier.weight(1f))
        }
        CowPicker(cows, cowId, onSelect = { cowId = it }, allowAll = true)
        Button(
            onClick = {
                val entry = MilkEntryEntity(
                    date = parseDate(date) ?: System.currentTimeMillis(),
                    cowId = cowId,
                    liters = liters.toDoubleOrNull().orZero(),
                    fat = fat.toDoubleOrNull().orZero(),
                    snf = snf.toDoubleOrNull().orZero(),
                    amount = amount.toDoubleOrNull().orZero()
                )
                if (entry.liters <= 0.0 || entry.amount <= 0.0) {
                    Toast.makeText(context, "Enter liters and slip amount.", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        dao.insertMilkEntry(entry)
                        liters = ""
                        fat = ""
                        snf = ""
                        amount = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Add Milk Slip", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExpenseScreen(dao: DairyDao, cows: List<CowEntity>) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(DateFormat.format(Date())) }
    var category by remember { mutableStateOf("Fodder") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cowId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    Panel {
        Text("Expense Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(14.dp))
        MoneyTextField("Date", date, { date = it }, KeyboardType.Text)
        MoneyTextField("Amount", amount, { amount = it }, KeyboardType.Decimal)
        MoneyTextField("Note", note, { note = it }, KeyboardType.Text)
        Text("Category", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpenseCategories.forEach { item ->
                FilterChip(
                    selected = category == item,
                    onClick = { category = item },
                    label = { Text(item) }
                )
            }
        }
        CowPicker(cows, cowId, onSelect = { cowId = it }, allowAll = true)
        Button(
            onClick = {
                val expense = ExpenseEntryEntity(
                    date = parseDate(date) ?: System.currentTimeMillis(),
                    cowId = cowId,
                    category = category,
                    amount = amount.toDoubleOrNull().orZero(),
                    note = note.trim()
                )
                if (expense.amount <= 0.0) {
                    Toast.makeText(context, "Enter expense amount.", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        dao.insertExpenseEntry(expense)
                        amount = ""
                        note = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Add Expense", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CowsScreen(dao: DairyDao, cowProfits: List<CowProfit>) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            Text("Cow-wise Analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MoneyTextField("Cow name", name, { name = it }, KeyboardType.Text, Modifier.weight(1f))
                MoneyTextField("Breed", breed, { breed = it }, KeyboardType.Text, Modifier.weight(1f))
            }
            Button(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Enter cow name.", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            dao.insertCow(CowEntity(name = name.trim(), breed = breed.trim()))
                            name = ""
                            breed = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add Cow", fontWeight = FontWeight.Bold)
            }
        }
        Panel {
            Text("Profit by Cow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            if (cowProfits.isEmpty()) {
                Text("Add cows and tag income entries to compare productivity.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                cowProfits.forEach { row ->
                    CowProfitRow(row)
                }
            }
        }
    }
}

@Composable
private fun CowProfitRow(row: CowProfit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.cow.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(
                text = listOf(
                    row.cow.breed.takeIf { it.isNotBlank() },
                    "${oneDecimal(row.liters)} L milk",
                    "${money(row.income / max(1.0, row.liters))} / L"
                ).filterNotNull().joinToString("  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            money(row.profit),
            color = if (row.profit >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun CowPicker(
    cows: List<CowEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    allowAll: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Text("Cow", fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (allowAll) {
                FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Herd") })
            }
            cows.forEach { cow ->
                FilterChip(
                    selected = selected == cow.id,
                    onClick = { onSelect(cow.id) },
                    label = { Text(cow.name) }
                )
            }
        }
    }
}

@Composable
private fun MoneyTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(18.dp),
        content = content
    )
}

private val chartColors = listOf(
    Color(0xFF5DD39E),
    Color(0xFFFF7D86),
    Color(0xFFFFC857),
    Color(0xFF64B5F6),
    Color(0xFFB39DDB)
)

private fun buildMonthlySummary(
    cows: List<CowEntity>,
    milkEntries: List<MilkEntryEntity>,
    expenseEntries: List<ExpenseEntryEntity>
): MonthlySummary {
    val income = milkEntries.sumOf { it.amount }
    val expenses = expenseEntries.sumOf { it.amount }
    val liters = milkEntries.sumOf { it.liters }
    val net = income - expenses
    val perLiter = if (liters > 0.0) net / liters else 0.0
    val byCategory = ExpenseCategories.associateWith { category ->
        expenseEntries.filter { it.category == category }.sumOf { it.amount }
    }
    val recent = milkEntries.map {
        RecentEntry(
            date = it.date,
            title = "Milk slip",
            subtitle = "${oneDecimal(it.liters)} L  Fat ${oneDecimal(it.fat)}%  SNF ${oneDecimal(it.snf)}%",
            amount = it.amount,
            income = true
        )
    } + expenseEntries.map {
        RecentEntry(
            date = it.date,
            title = it.category,
            subtitle = it.note,
            amount = it.amount,
            income = false
        )
    }
    val sharedExpenses = expenseEntries.filter { it.cowId == null }.sumOf { it.amount }
    val cowProfits = cows.map { cow ->
        val cowMilk = milkEntries.filter { it.cowId == cow.id }
        val cowLiters = cowMilk.sumOf { it.liters }
        val cowIncome = cowMilk.sumOf { it.amount }
        val directExpenses = expenseEntries.filter { it.cowId == cow.id }.sumOf { it.amount }
        val sharedAllocation = if (liters > 0.0) sharedExpenses * (cowLiters / liters) else 0.0
        CowProfit(
            cow = cow,
            liters = cowLiters,
            income = cowIncome,
            expenses = directExpenses + sharedAllocation,
            profit = cowIncome - directExpenses - sharedAllocation
        )
    }.sortedBy { it.profit }

    return MonthlySummary(
        income = income,
        expenses = expenses,
        liters = liters,
        netProfit = net,
        profitPerLiter = perLiter,
        expensesByCategory = byCategory,
        recentEntries = recent.sortedByDescending { it.date },
        cowProfits = cowProfits
    )
}

private fun coachAdvice(summary: MonthlySummary): List<String> {
    if (summary.income <= 0.0 && summary.expenses <= 0.0) {
        return listOf("Start with today's milk slip and one expense entry. The dashboard will show input cost, gross income, and profit per liter automatically.")
    }
    val lines = mutableListOf<String>()
    val biggest = summary.expensesByCategory.maxByOrNull { it.value }
    if (summary.expenses > 0.0 && biggest != null && biggest.value > 0.0) {
        val share = (biggest.value / summary.expenses * 100).toInt()
        lines += when (biggest.key) {
            "Fodder" -> "Fodder is taking $share% of this month's cost. Try replacing one purchased feed cycle with home-grown green fodder, silage, or crop residue planning."
            "Medical" -> "Medical costs are $share% of expenses. Track repeat vet visits cow-wise and check vaccination, hygiene, and mineral mix routines."
            "Labor" -> "Labor is $share% of expenses. Review daily milking and cleaning routines for tasks that can be batched without reducing animal care."
            "Electricity" -> "Electricity is $share% of expenses. Shift pumping and chilling work to efficient hours and inspect motor usage."
            else -> "Other costs are $share% of expenses. Split large notes into clearer categories so the real cost driver is visible."
        }
    }
    lines += if (summary.netProfit < 0.0) {
        "This month is in loss. Pause non-essential purchases and check if milk payment rate, fat percent, or a low-yield cow is pulling down the herd."
    } else if (summary.profitPerLiter < 5.0) {
        "Profit is positive but thin. Aim to raise profit per liter by improving fat/SNF quality and reducing the biggest expense category first."
    } else {
        "Financial health is green. Keep logging daily slips so this profitable pattern is visible across the full year."
    }
    val lowestCow = summary.cowProfits.firstOrNull { it.liters > 0.0 }
    if (lowestCow != null && lowestCow.profit < 0.0) {
        lines += "${lowestCow.cow.name} is currently negative after allocated costs. Review feed, health, and yield before deciding whether to retain or sell."
    }
    return lines
}

private fun currentMonth(): MonthCursor {
    val calendar = Calendar.getInstance()
    return MonthCursor(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
}

private fun MonthCursor.shift(delta: Int): MonthCursor {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.YEAR, year)
    calendar.set(Calendar.MONTH, month)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.add(Calendar.MONTH, delta)
    return MonthCursor(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
}

private fun monthBounds(month: MonthCursor): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.YEAR, month.year)
        set(Calendar.MONTH, month.month)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = start.clone() as Calendar
    end.add(Calendar.MONTH, 1)
    end.add(Calendar.MILLISECOND, -1)
    return start.timeInMillis to end.timeInMillis
}

private fun MonthCursor.title(): String {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.YEAR, year)
    calendar.set(Calendar.MONTH, month)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    return SimpleDateFormat("MMMM yyyy", Locale.US).format(calendar.time)
}

private fun parseDate(value: String): Long? {
    return runCatching { DateFormat.parse(value.trim())?.time }.getOrNull()
}

private fun money(value: Double): String {
    val rounded = kotlin.math.round(value).toLong()
    return "Rs $rounded"
}

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun shortDate(date: Long): String = SimpleDateFormat("dd MMM", Locale.US).format(Date(date))

private fun Double?.orZero(): Double = this ?: 0.0

private fun sharePdf(context: Context, month: String, summary: MonthlySummary) {
    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "ksheera-sagara-$month.pdf".replace(" ", "-"))
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(14, 28, 21)
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(56, 74, 64)
            textSize = 15f
        }
        val bold = Paint(body).apply {
            color = AndroidColor.rgb(14, 28, 21)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
        }
        canvas.drawColor(AndroidColor.rgb(244, 248, 241))
        canvas.drawText("Ksheera-Sagara", 42f, 64f, title)
        canvas.drawText("Monthly Financial Summary - $month", 42f, 94f, body)
        var y = 150f
        listOf(
            "Income" to money(summary.income),
            "Expenses" to money(summary.expenses),
            "Net Profit" to money(summary.netProfit),
            "Profit per Liter" to money(summary.profitPerLiter),
            "Milk Logged" to "${oneDecimal(summary.liters)} L"
        ).forEach { (label, value) ->
            canvas.drawText(label, 42f, y, body)
            canvas.drawText(value, 340f, y, bold)
            y += 38f
        }
        y += 20f
        canvas.drawText("Expense Mix", 42f, y, bold)
        y += 34f
        summary.expensesByCategory.filter { it.value > 0.0 }.forEach { (category, amount) ->
            canvas.drawText(category, 62f, y, body)
            canvas.drawText(money(amount), 340f, y, body)
            y += 28f
        }
        y += 26f
        canvas.drawText("AI Profit Coach", 42f, y, bold)
        y += 32f
        coachAdvice(summary).forEach { line ->
            canvas.drawText(line.take(72), 42f, y, body)
            y += 26f
        }
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }.onFailure {
        Toast.makeText(context, "Could not create PDF.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareImage(context: Context, month: String, summary: MonthlySummary) {
    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "ksheera-sagara-$month.png".replace(" ", "-"))
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(AndroidColor.rgb(10, 18, 14))
        paint.color = AndroidColor.WHITE
        paint.textSize = 70f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Ksheera-Sagara", 70f, 120f, paint)
        paint.textSize = 36f
        paint.typeface = Typeface.DEFAULT
        paint.color = AndroidColor.rgb(184, 199, 190)
        canvas.drawText(month, 70f, 175f, paint)
        paint.color = AndroidColor.rgb(17, 28, 22)
        canvas.drawRoundRect(60f, 240f, 1020f, 670f, 24f, 24f, paint)
        paint.color = if (summary.netProfit >= 0.0) AndroidColor.rgb(99, 216, 165) else AndroidColor.rgb(255, 125, 134)
        paint.textSize = 88f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(money(summary.netProfit), 100f, 360f, paint)
        paint.color = AndroidColor.WHITE
        paint.textSize = 34f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Net profit this month", 100f, 415f, paint)
        paint.textSize = 40f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Income ${money(summary.income)}", 100f, 520f, paint)
        canvas.drawText("Expenses ${money(summary.expenses)}", 100f, 585f, paint)
        canvas.drawText("Per Liter ${money(summary.profitPerLiter)}", 100f, 650f, paint)
        paint.color = AndroidColor.WHITE
        paint.textSize = 46f
        canvas.drawText("AI Profit Coach", 70f, 780f, paint)
        paint.color = AndroidColor.rgb(218, 231, 223)
        paint.textSize = 33f
        paint.typeface = Typeface.DEFAULT
        var y = 845f
        coachAdvice(summary).forEach { line ->
            canvas.drawText(line.take(58), 70f, y, paint)
            y += 54f
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 96, it) }
        shareFile(context, file, "image/png")
    }.onFailure {
        Toast.makeText(context, "Could not create image.", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share monthly summary"))
}
