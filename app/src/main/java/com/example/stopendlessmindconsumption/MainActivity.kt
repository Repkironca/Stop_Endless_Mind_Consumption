package com.example.stopendlessmindconsumption

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// --- 資料結構定義區 ---
// [MindRecord]
// 增加了 severity (1=藍, 2=紫, 3=紅) 和 note (日記)
data class MindRecord(
    val timestamp: Long,
    val severity: Int,
    val note: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 改用 AppNavigation 來管理頁面切換
                AppNavigation()
            }
        }
    }
}

// 顏色定義
val colorBlue = Color(0xFF00008b)
val colorPurple = Color(0xFF5B0E53)
val colorBloodRed = Color(0xFFB71C1C)

// --- 頂層導航控制 (新) ---
// 負責在主畫面與歷史畫面之間切換，並持有共用的 records 狀態
@Composable
fun AppNavigation() {
    // [Context]: Android 的上帝物件
    val context = LocalContext.current
    // [SharedPreferences]: 輕量級資料庫
    val sharedPref = remember { context.getSharedPreferences("StopLossData", Context.MODE_PRIVATE) }

    // [State]: 將狀態提升到這裡，讓兩個頁面共用
    var records by remember { mutableStateOf(RecordManager.loadRecords(sharedPref)) }
    var maxLimit by remember { mutableIntStateOf(sharedPref.getInt("MAX_LIMIT", 50)) }

    // 新增：冷卻時間 (單位：分鐘)，預設 60 分鐘
    var coolingTime by remember { mutableIntStateOf(sharedPref.getInt("COOLING_TIME", 60)) }

    // 頁面狀態: "home" or "history"
    var currentScreen by remember { mutableStateOf("home") }

    if (currentScreen == "home") {
        StopLossScreen(
            records = records,
            maxLimit = maxLimit,
            coolingTime = coolingTime,
            onRecordsChange = { newRecords ->
                records = newRecords
                RecordManager.saveRecords(sharedPref, newRecords)
            },
            onSettingsChange = { newLimit, newCooling ->
                maxLimit = newLimit
                coolingTime = newCooling
                sharedPref.edit()
                    .putInt("MAX_LIMIT", newLimit)
                    .putInt("COOLING_TIME", newCooling)
                    .apply()
            },
            onNavigateToHistory = { currentScreen = "history" }
        )
    } else {
        HistoryScreen(
            records = records,
            coolingTime = coolingTime,
            onBack = { currentScreen = "home" }
        )
    }
}

// --- 1. 主畫面 (修改版) ---
// 現在接收參數而非自己管理狀態，保留原有的註解與邏輯
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopLossScreen(
    records: List<MindRecord>,
    maxLimit: Int,
    coolingTime: Int, // 新增：傳入冷卻時間
    onRecordsChange: (List<MindRecord>) -> Unit,
    onSettingsChange: (Int, Int) -> Unit, // 修改：同時更新上限與冷卻時間
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current

    // 控制視窗顯示的開關
    var showSettings by remember { mutableStateOf(false) }
    var showDiaryInput by remember { mutableStateOf(false) }

    // 暫存變數：用來記錄使用者剛剛按了哪個顏色的按鈕
    var tempSeverity by remember { mutableIntStateOf(1) }

    // 計算目前的次數
    val currentCount = records.size
    // 計算進度百分比 (0.0 ~ 1.0)
    val progress = (currentCount.toFloat() / maxLimit).coerceIn(0f, 1f)

    // 格式化上次紀錄時間
    val lastRecord = records.lastOrNull()
    val lastRecordText = if (lastRecord != null) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        "上次紀錄：${sdf.format(Date(lastRecord.timestamp))}"
    } else {
        "尚未開始紀錄"
    }

    // [Scaffold]: 建築鷹架
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("沒關係的小石頭，等你想跑再跑吧", fontWeight = FontWeight.Bold) },
                actions = {
                    // 新增：歷史紀錄按鈕
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "History")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->

        // --- 主畫面內容 ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 頂部資訊
            Text(text = "停損點: $maxLimit 次", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)

            // 新增：目前次數顯示 (介於停損點與百分比之間)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "目前紀錄: $currentCount 次",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 顯示百分比字樣 (修改：保留兩位小數)
            Text(
                text = "%.2f%%".format(progress * 100),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if(progress > 0.8f) Color(0xFFB71C1C) else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- 視覺區：改回只有派大星 (放大版) ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(300.dp).fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.patrick),
                    contentDescription = "Patrick",
                    modifier = Modifier.size(300.dp), // 這裡把它放大回 300dp
                    contentScale = ContentScale.Fit
                )
            }

            // 新增：在派大星下面顯示上次紀錄時間
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = lastRecordText,
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f)) // 彈簧

            // --- 控制區：三顆按鈕 (顏色修改：冷寂藍->瘀青紫->血紅) ---
            Text("選擇嚴重程度", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 藍色按鈕 (Level 1: 冷寂灰藍)
                SeverityButton(color = colorBlue, text = "", textColor = Color.White) {
                    if (checkCooldown(records, context, coolingTime)) {
                        tempSeverity = 1
                        showDiaryInput = true
                    }
                }
                // 紫色按鈕 (Level 2: 瘀青紫)
                SeverityButton(color = colorPurple, text = "", textColor = Color.White) {
                    if (checkCooldown(records, context, coolingTime)) {
                        tempSeverity = 2
                        showDiaryInput = true
                    }
                }
                // 紅色按鈕 (Level 3: 凝固血紅)
                SeverityButton(color = colorBloodRed, text = "", textColor = Color.White) {
                    if (checkCooldown(records, context, coolingTime)) {
                        tempSeverity = 3
                        showDiaryInput = true
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // --- 彈出視窗區 ---

    // 1. 設定視窗
    if (showSettings) {
        SettingsDialog(
            currentLimit = maxLimit,
            currentCooling = coolingTime,
            onDismiss = { showSettings = false },
            onConfirm = { newLimit, newCooling ->
                onSettingsChange(newLimit, newCooling)
                showSettings = false
                Toast.makeText(context, "設定已更新", Toast.LENGTH_SHORT).show()
            },
            onResetData = {
                onRecordsChange(emptyList())
                showSettings = false
                Toast.makeText(context, "資料已歸零", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. 日記輸入視窗
    if (showDiaryInput) {
        DiaryInputDialog(
            onDismiss = { showDiaryInput = false },
            onConfirm = { noteContent ->
                val newRecord = MindRecord(
                    timestamp = System.currentTimeMillis(),
                    severity = tempSeverity,
                    note = noteContent
                )
                onRecordsChange(records + newRecord)

                showDiaryInput = false

                if (records.size + 1 >= maxLimit) { // +1 因為 records 還沒更新
                    Toast.makeText(context, "停損點已到，可以認真評估目前狀態了", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "紀錄已儲存", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// --- 2. 歷史紀錄畫面 (Phase 3 新增) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<MindRecord>,
    coolingTime: Int,
    onBack: () -> Unit
) {
    // 顯示模式: true = Grid (格子), false = List (列表)
    var isGridMode by remember { mutableStateOf(true) }

    // 選中的日期 (timestamp, 代表該日 00:00)，不為 null 時顯示彈窗
    var selectedDayStart by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isGridMode) "歷史紀錄 (月曆)" else "歷史紀錄 (時間軸)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // 切換顯示模式
                    IconButton(onClick = { isGridMode = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Grid", tint = if (isGridMode) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                    IconButton(onClick = { isGridMode = false }) {
                        Icon(Icons.Default.List, contentDescription = "List", tint = if (!isGridMode) MaterialTheme.colorScheme.primary else Color.Gray)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (isGridMode) {
                // 格子模式
                CalendarGridView(records) { dayStart -> selectedDayStart = dayStart }
            } else {
                // 列表(時間軸)模式
                TimelineListView(records, coolingTime) { dayStart -> selectedDayStart = dayStart }
            }
        }
    }

    // 詳細資訊懸浮視窗
    if (selectedDayStart != null) {
        DetailPopup(
            dayStart = selectedDayStart!!,
            allRecords = records,
            coolingTime = coolingTime,
            onDismiss = { selectedDayStart = null }
        )
    }
}

// --- 2.1 格子模式實作 (Github Style) ---
@Composable
fun CalendarGridView(records: List<MindRecord>, onDateClick: (Long) -> Unit) {
    // 產生過去 12 個月
    val months = remember {
        val list = mutableListOf<Calendar>()
        val cal = Calendar.getInstance()
        // 為了讓最近的月份在最上面，我們生成完列表後不反轉(或者看你習慣)，這裡預設從本月開始往下是過去
        for (i in 0 until 12) {
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            list.add(c)
            cal.add(Calendar.MONTH, -1)
        }
        list
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(months) { monthCal ->
            MonthSection(monthCal, records, onDateClick)
            HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
        }
    }
}

@Composable
fun MonthSection(monthCal: Calendar, allRecords: List<MindRecord>, onDateClick: (Long) -> Unit) {
    val year = monthCal.get(Calendar.YEAR)
    val month = monthCal.get(Calendar.MONTH) + 1
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = monthCal.get(Calendar.DAY_OF_WEEK) // 1=Sun

    val monthStart = monthCal.timeInMillis
    val nextMonth = monthCal.clone() as Calendar
    nextMonth.add(Calendar.MONTH, 1)
    val monthEnd = nextMonth.timeInMillis

    // 篩選當月紀錄
    val recordsInMonth = allRecords.filter { it.timestamp in monthStart until monthEnd }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("$year 年 $month 月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // 星期頭
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        // 計算格子
        val totalSlots = daysInMonth + (firstDayOfWeek - 1)
        val rows = (totalSlots + 6) / 7

        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val day = index - (firstDayOfWeek - 1) + 1

                    if (day in 1..daysInMonth) {
                        val dayCal = monthCal.clone() as Calendar
                        dayCal.set(Calendar.DAY_OF_MONTH, day)
                        val dStart = dayCal.timeInMillis
                        val dEnd = dStart + 86400000L
                        val dailyRecs = recordsInMonth.filter { it.timestamp in dStart until dEnd }

                        // 顏色邏輯：無紀錄=綠色，有紀錄=計算加權平均顏色
                        val cellColor = if (dailyRecs.isEmpty()) {
                            Color(0xFF4CAF50) // 綠色 (Safe)
                        } else {
                            val avg = dailyRecs.map { it.severity }.average()
                            // 使用漸層混色 (深藍->深紫->血紅)
                            calculateWeightedColor(avg)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(cellColor)
                                .clickable { onDateClick(dStart) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", fontSize = 12.sp, color = if(dailyRecs.isEmpty()) Color.Black else Color.White)
                                if (dailyRecs.isNotEmpty()) {
                                    Text("${dailyRecs.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

// --- 2.2 列表模式 (時間軸) 實作 ---
@Composable
fun TimelineListView(records: List<MindRecord>, coolingTime: Int, onDateClick: (Long) -> Unit) {
    // 顯示過去 60 天
    val days = remember {
        val list = mutableListOf<Calendar>()
        val cal = Calendar.getInstance()
        for (i in 0 until 60) {
            val c = cal.clone() as Calendar
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            list.add(c)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        list
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(days) { dayCal ->
            TimelineItem(dayCal, records, coolingTime, onDateClick)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TimelineItem(dayCal: Calendar, allRecords: List<MindRecord>, coolingTime: Int, onDateClick: (Long) -> Unit) {
    val dayStart = dayCal.timeInMillis
    val dayEnd = dayStart + 86400000L
    val dateStr = SimpleDateFormat("MM/dd (E)", Locale.getDefault()).format(dayCal.time)

    // 計算冷卻時間的毫秒數
    val coolingMillis = coolingTime * 60000L

    // 找出可能影響這天的紀錄 (包含前一天跨日的)
    // 搜尋範圍要擴大到：dayStart - coolingTime
    val searchStart = dayStart - coolingMillis
    val relevantRecords = allRecords.filter { it.timestamp in searchStart until dayEnd }

    Column(modifier = Modifier.fillMaxWidth().clickable { onDateClick(dayStart) }) {
        Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            val w = size.width
            val h = size.height
            val totalMinutes = 24 * 60f

            // 1. 底色全綠
            drawRect(color = Color(0xFF4CAF50), size = size)

            // 2. 畫上紀錄 (使用設定的 coolingTime)
            relevantRecords.forEach { rec ->
                // 計算紀錄相對於今天 00:00 的開始與結束分鐘數
                // 注意：如果 rec.timestamp < dayStart (昨天的紀錄)，startMin 會是負的
                val startMin = (rec.timestamp - dayStart) / 60000f
                val endMin = startMin + coolingTime.toFloat() // 持續 coolingTime 分鐘

                // 計算繪製範圍 (截斷超出當天的部分)
                val drawStartMin = startMin.coerceAtLeast(0f)
                val drawEndMin = endMin.coerceAtMost(totalMinutes)

                if (drawEndMin > drawStartMin) {
                    val startX = (drawStartMin / totalMinutes) * w
                    val endX = (drawEndMin / totalMinutes) * w
                    val color = when(rec.severity) {
                        1 -> colorBlue
                        2 -> colorPurple
                        else -> colorBloodRed
                    }

                    drawRect(
                        color = color,
                        topLeft = Offset(startX, 0f),
                        size = Size(endX - startX, h)
                    )
                }
            }
        }
    }
}

// --- 3. 詳細資訊懸浮視窗 ---
@Composable
fun DetailPopup(dayStart: Long, allRecords: List<MindRecord>, coolingTime: Int, onDismiss: () -> Unit) {
    val dayEnd = dayStart + 86400000L
    val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val coolingMillis = coolingTime * 60000L

    // 篩選出顯示在詳細清單的紀錄
    // 包含：今天發生的，以及昨天發生但跨到今天的
    val recordsToShow = allRecords.filter { rec ->
        val recEnd = rec.timestamp + coolingMillis
        // 條件：紀錄本身的區間 [t, t+cooling] 與今天的區間 [0, 24h] 有重疊
        rec.timestamp < dayEnd && recEnd > dayStart
    }.sortedBy { it.timestamp }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${dateFormat.format(Date(dayStart))} 詳細紀錄",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (recordsToShow.isEmpty()) {
                    Text("這天心如止水，沒有任何紀錄。", color = Color.Gray)
                } else {
                    LazyColumn {
                        items(recordsToShow) { record ->
                            // 判斷是否為跨日
                            val isCrossDay = record.timestamp < dayStart

                            Row(verticalAlignment = Alignment.Top) {
                                val color = when(record.severity) {
                                    1 -> Color(0xFF37474F) // 冷寂灰藍
                                    2 -> Color(0xFF4A148C) // 瘀青紫
                                    else -> Color(0xFFB71C1C) // 凝固血紅
                                }
                                Box(modifier = Modifier.padding(top = 4.dp).size(12.dp).clip(RoundedCornerShape(50)).background(color))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(timeFormat.format(Date(record.timestamp)), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (isCrossDay) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("(跨日延續)", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    if (record.note.isNotEmpty()) {
                                        Text(record.note, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("關閉")
                }
            }
        }
    }
}

// --- Composable 元件區 (保留原樣) ---

@Composable
fun SeverityButton(color: Color, text: String, textColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.size(width = 100.dp, height = 60.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// 日記輸入對話框
@Composable
fun DiaryInputDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("寫點什麼吧...") },
        text = {
            Column {
                Text("記錄任何東西，如果你懶的話也能跳過就是")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("日記內容") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 設定視窗 (新增：冷卻時間設定)
@Composable
fun SettingsDialog(
    currentLimit: Int,
    currentCooling: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit, // 修改回傳參數
    onResetData: () -> Unit
) {
    var tempLimitString by remember { mutableStateOf(currentLimit.toString()) }
    var tempCoolingString by remember { mutableStateOf(currentCooling.toString()) }
    var showResetConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定與開發者資訊") },
        text = {
            Column {
                Text("開發者: Repkironca")
                Text("版本: v0.4.1 (Beta)") // 更新版本號
                Spacer(modifier = Modifier.height(16.dp))

                Text("設定停損點 (次數):", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = tempLimitString,
                    onValueChange = { if (it.all { char -> char.isDigit() }) tempLimitString = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("設定冷卻時間 (分鐘):", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = tempCoolingString,
                    onValueChange = { if (it.all { char -> char.isDigit() }) tempCoolingString = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { showResetConfirm = true }) {
                    Text("清除所有紀錄 (歸零)", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    tempLimitString.toIntOrNull() ?: currentLimit,
                    tempCoolingString.toIntOrNull() ?: currentCooling
                )
            }) {
                Text("確定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("警告") },
            text = { Text("確定要刪除所有紀錄嗎？") },
            confirmButton = {
                Button(onClick = { onResetData(); showResetConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("確認刪除")
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("取消") } }
        )
    }
}

// --- 靜態工具區 ---

// 計算加權顏色 (改用冷暖色調混色)
// 1.0 = 冷寂灰藍 (Blue Grey), 2.0 = 瘀青紫 (Deep Purple), 3.0 = 凝固血紅 (Blood Red)
fun calculateWeightedColor(avg: Double): Color {
    // 定義顏色常數
    return when {
        avg <= 1.0 -> colorBlue
        avg >= 3.0 -> colorBloodRed
        avg <= 2.0 -> {
            // 在 灰藍 (1.0) 與 深紫 (2.0) 之間插值
            val fraction = (avg - 1.0).toFloat() // 0.0 ~ 1.0
            lerpColor(colorBlue, colorPurple, fraction)
        }
        else -> {
            // 在 深紫 (2.0) 與 血紅 (3.0) 之間插值
            val fraction = (avg - 2.0).toFloat() // 0.0 ~ 1.0
            lerpColor(colorPurple, colorBloodRed, fraction)
        }
    }
}

// 顏色線性插值 (Linear Interpolation)
fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val r = start.red + (stop.red - start.red) * fraction
    val g = start.green + (stop.green - start.green) * fraction
    val b = start.blue + (stop.blue - start.blue) * fraction
    val a = start.alpha + (stop.alpha - start.alpha) * fraction
    return Color(r, g, b, a)
}

fun checkCooldown(records: List<MindRecord>, context: Context, coolingTimeMinutes: Int): Boolean {
    val now = System.currentTimeMillis()
    val lastTime = records.lastOrNull()?.timestamp ?: 0L

    // 計算毫秒數
    val coolingMillis = coolingTimeMinutes * 60000L

    if (now - lastTime < coolingMillis) {
        val remainingMin = (coolingMillis - (now - lastTime)) / 60000
        val remainingSec = ((coolingMillis - (now - lastTime)) % 60000) / 1000
        Toast.makeText(context, "紀錄過於頻繁，尚在冷卻中！還需 $remainingMin 分 $remainingSec 秒。", Toast.LENGTH_SHORT).show()
        return false
    }
    return true
}

object RecordManager {
    fun saveRecords(pref: android.content.SharedPreferences, records: List<MindRecord>) {
        val jsonArray = JSONArray()
        records.forEach { record ->
            val jsonObject = JSONObject()
            jsonObject.put("t", record.timestamp)
            jsonObject.put("s", record.severity)
            jsonObject.put("n", record.note)
            jsonArray.put(jsonObject)
        }
        pref.edit().putString("RECORDS_JSON", jsonArray.toString()).apply()
    }

    fun loadRecords(pref: android.content.SharedPreferences): List<MindRecord> {
        val jsonString = pref.getString("RECORDS_JSON", "[]") ?: "[]"
        val list = mutableListOf<MindRecord>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(MindRecord(
                    timestamp = obj.getLong("t"),
                    severity = obj.optInt("s", 1),
                    note = obj.optString("n", "")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}