package com.example.stopendlessmindconsumption

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

// --- 資料結構定義區 ---
// [MindRecord]
// 增加了 severity (1=黃, 2=橘, 3=紅) 和 note (日記)
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
                StopLossScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopLossScreen() {
    // [Context]: Android 的上帝物件
    val context = LocalContext.current

    // [SharedPreferences]: 輕量級資料庫
    val sharedPref = remember { context.getSharedPreferences("StopLossData", Context.MODE_PRIVATE) }

    // [State]: 畫面狀態變數
    var records by remember { mutableStateOf(RecordManager.loadRecords(sharedPref)) }
    var maxLimit by remember { mutableIntStateOf(sharedPref.getInt("MAX_LIMIT", 50)) }

    // 控制視窗顯示的開關
    var showSettings by remember { mutableStateOf(false) }
    var showDiaryInput by remember { mutableStateOf(false) }

    // 暫存變數：用來記錄使用者剛剛按了哪個顏色的按鈕
    var tempSeverity by remember { mutableIntStateOf(1) }

    // 計算目前的次數
    val currentCount = records.size
    // 計算進度百分比 (0.0 ~ 1.0)
    val progress = (currentCount.toFloat() / maxLimit).coerceIn(0f, 1f)

    // [Scaffold]: 建築鷹架
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("露西停損計畫", fontWeight = FontWeight.Bold) },
                actions = {
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
            Text(text = "停損點: $maxLimit", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)

            // 顯示百分比字樣 (修改：保留兩位小數)
            Text(
                text = "%.2f%%".format(progress * 100),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if(progress > 0.8f) Color.Red else MaterialTheme.colorScheme.primary
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

            Spacer(modifier = Modifier.weight(1f)) // 彈簧

            // --- 控制區：三顆按鈕 ---
            Text("選擇嚴重程度", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 黃色按鈕
                SeverityButton(color = Color(0xFFFFEB3B), text = "", textColor = Color.Black) {
                    if (checkCooldown(records, context)) {
                        tempSeverity = 1
                        showDiaryInput = true
                    }
                }
                // 橘色按鈕
                SeverityButton(color = Color(0xFFFF9800), text = "", textColor = Color.White) {
                    if (checkCooldown(records, context)) {
                        tempSeverity = 2
                        showDiaryInput = true
                    }
                }
                // 紅色按鈕
                SeverityButton(color = Color(0xFFF44336), text = "", textColor = Color.White) {
                    if (checkCooldown(records, context)) {
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
            onDismiss = { showSettings = false },
            onConfirm = { newLimit ->
                maxLimit = newLimit
                sharedPref.edit().putInt("MAX_LIMIT", newLimit).apply()
                showSettings = false
                Toast.makeText(context, "停損點已更新", Toast.LENGTH_SHORT).show()
            },
            onResetData = {
                records = emptyList()
                RecordManager.saveRecords(sharedPref, records)
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
                records = records + newRecord
                RecordManager.saveRecords(sharedPref, records)

                showDiaryInput = false

                if (records.size >= maxLimit) {
                    Toast.makeText(context, "停損點到了！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "紀錄已儲存", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

// --- Composable 元件區 ---

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
                    maxLines = 5
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

// 設定視窗
@Composable
fun SettingsDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onResetData: () -> Unit
) {
    var tempLimitString by remember { mutableStateOf(currentLimit.toString()) }
    var showResetConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定與開發者資訊") },
        text = {
            Column {
                Text("開發者: 資工系暈船仔")
                Text("版本: v0.2.0 (Beta)")
                Spacer(modifier = Modifier.height(16.dp))

                Text("設定停損點:", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = tempLimitString,
                    onValueChange = { if (it.all { char -> char.isDigit() }) tempLimitString = it },
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
            Button(onClick = { onConfirm(tempLimitString.toIntOrNull() ?: currentLimit) }) {
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

fun checkCooldown(records: List<MindRecord>, context: Context): Boolean {
    val now = System.currentTimeMillis()
    val lastTime = records.lastOrNull()?.timestamp ?: 0L
    // 正式版建議改成 3600000 (1小時)，目前保留 5000 (5秒) 供測試
    if (now - lastTime < 5000) {
        Toast.makeText(context, "太頻繁了！還在冷卻中。", Toast.LENGTH_SHORT).show()
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