package com.example.stopendlessmindconsumption

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// --- 資料結構定義區 ---
// 這就是我們的每一筆紀錄，包含時間、嚴重程度(目前先預設1)、筆記
data class MindRecord(
    val timestamp: Long,
    val severity: Int, // 1=黃, 2=橘, 3=紅 (之後會用到)
    val note: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // MaterialTheme 是 Android 的預設外觀風格包
            MaterialTheme {
                StopLossScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // 告訴編譯器我知道 TopAppBar 是實驗性功能，請忽略警告
@Composable
fun StopLossScreen() {
    // [Context]: Android 的上帝物件，代表「目前的應用程式環境」。存檔、跳通知都需要它。
    val context = LocalContext.current

    // [SharedPreferences]: 一個超輕量級的 Key-Value 資料庫，適合存設定檔或簡單的紀錄。
    val sharedPref = remember { context.getSharedPreferences("StopLossData", Context.MODE_PRIVATE) }

    // [State]: 這裡的變數只要被修改，UI 就會自動重畫 (Re-composition)。
    // 讀取紀錄列表
    var records by remember { mutableStateOf(RecordManager.loadRecords(sharedPref)) }
    // 讀取停損點上限
    var maxLimit by remember { mutableIntStateOf(sharedPref.getInt("MAX_LIMIT", 50)) }

    // 控制設定視窗要不要顯示
    var showSettings by remember { mutableStateOf(false) }

    // 計算目前的次數
    val currentCount = records.size

    // [Scaffold]: 建築鷹架。Android 用來快速蓋出「頂部欄 + 內容區 + 浮動按鈕」的標準結構。
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("露西停損計畫", fontWeight = FontWeight.Bold) },
                actions = {
                    // 右上角的設定按鈕
                    IconButton(onClick = { showSettings = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        // 主要的內容區
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // 避免內容被 TopBar 擋住
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(text = "目標停損點: $maxLimit", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(32.dp))

            // 派大星圖片
            Image(
                painter = painterResource(id = R.drawable.patrick),
                contentDescription = "Patrick",
                modifier = Modifier.size(280.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 進度條
            val progress = (currentCount.toFloat() / maxLimit).coerceIn(0f, 1f)
            Text(text = "目前累積消耗: $currentCount / $maxLimit", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 紀錄按鈕 (目前先用單一顆代替，之後 Phase 2 會改成三顆)
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    // 檢查是否冷卻 (這裡設 5秒 方便測試，之後改 3600000)
                    val lastTime = records.lastOrNull()?.timestamp ?: 0L

                    if (now - lastTime > 5000) {
                        // 新增一筆紀錄
                        val newRecord = MindRecord(now, 1, "快速紀錄")
                        // 更新列表 (注意：Compose 需要一個全新的 List 才會觸發更新，所以用 +)
                        records = records + newRecord

                        // 存檔
                        RecordManager.saveRecords(sharedPref, records)

                        if (records.size >= maxLimit) {
                            Toast.makeText(context, "停損點到了！該醒醒了。", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "紀錄成功 (+1)", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "冷卻中...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("我又內耗了 (+1)", fontSize = 18.sp)
            }
        }
    }

    // --- 設定視窗 (Dialog) ---
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
                records = emptyList() // 清空記憶體中的列表
                RecordManager.saveRecords(sharedPref, records) // 清空硬碟
                showSettings = false
                Toast.makeText(context, "資料已歸零", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// 這是設定視窗的 UI 元件
@Composable
fun SettingsDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onResetData: () -> Unit
) {
    var tempLimitString by remember { mutableStateOf(currentLimit.toString()) }
    var showResetConfirm by remember { mutableStateOf(false) } // 控制是否顯示「確認歸零」的二次確認

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("設定與開發者資訊") },
        text = {
            Column {
                Text("開發者: 資工系暈船仔")
                Text("版本: v0.1.0 (Alpha)")
                Spacer(modifier = Modifier.height(16.dp))

                Text("設定停損點:", fontWeight = FontWeight.Bold)
                // 輸入框
                OutlinedTextField(
                    value = tempLimitString,
                    onValueChange = { newValue ->
                        // 過濾非數字輸入
                        if (newValue.all { it.isDigit() }) {
                            tempLimitString = newValue
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))
                // 歸零按鈕 (紅色字)
                TextButton(onClick = { showResetConfirm = true }) {
                    Text("清除所有紀錄 (歸零)", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newLimit = tempLimitString.toIntOrNull() ?: currentLimit
                onConfirm(newLimit)
            }) {
                Text("確定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 二次確認歸零的視窗
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("警告") },
            text = { Text("確定要刪除所有紀錄嗎？此動作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        onResetData()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }
}

// --- 靜態工具區 (類似 C++ 的 static helper functions) ---
object RecordManager {
    // 這是用來把 List<Record> 轉成 JSON String 存進手機的工具
    // 因為 SharedPreferences 笨笨的，只能存字串

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