package com.example.stopendlessmindconsumption // 注意：如果你專案名稱不同，這行要保留你原本的 package name

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StopLossScreen()
        }
    }
}

// 這是主要的 UI 畫面，Vibe Coding 從這裡開始看
@Composable
fun StopLossScreen() {
    // 1. 取得目前的 Context (用來存檔用)
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("StopLossData", Context.MODE_PRIVATE)

    // 2. 定義變數 (State) - 這些變數一變，畫面就會自動重畫
    // 讀取目前的累積次數 (預設 0)
    var currentCount by remember { mutableIntStateOf(sharedPref.getInt("COUNT", 0)) }
    // 讀取上次按的時間 (毫秒)
    var lastTimeMs by remember { mutableLongStateOf(sharedPref.getLong("LAST_TIME", 0L)) }

    // 設定你的停損點 (你可以直接改這個數字，或是之後做個輸入框)
    val maxLimit = 10

    // 用來顯示上次紀錄的時間字串
    val lastTimeStr = if (lastTimeMs == 0L) "無紀錄" else SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(lastTimeMs))

    // 3. 畫面佈局 (Column = 由上往下排列)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- 標題區 ---
        Text(text = "露西停損計畫", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "目標停損點: $maxLimit", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(32.dp))

        // --- 圖片區 (你的派大星) ---
        // 這裡會讀取 drawable 裡的 patrick.png
        Image(
            painter = painterResource(id = R.drawable.patrick),
            contentDescription = "Patrick",
            modifier = Modifier.size(300.dp).padding(8.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- 進度條區 (視覺化小石頭) ---
        // 計算目前的進度 (0.0 ~ 1.0)
        val progress = (currentCount.toFloat() / maxLimit).coerceIn(0f, 1f)

        Text(text = "目前累積消耗: $currentCount / $maxLimit", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // 進度條元件
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(20.dp),
        )

        Spacer(modifier = Modifier.height(48.dp))

        // --- 按鈕區 ---
        Button(
            onClick = {
                val now = System.currentTimeMillis()
                // 邏輯判斷：距離上次紀錄是否超過 1 小時 (3600000 毫秒)
                // 為了測試方便，我先改成 5 秒 (5000)，你測試完把 5000 改成 3600000 即可
                if (now - lastTimeMs > 5000) {
                    currentCount++
                    lastTimeMs = now

                    // 存入手機硬碟
                    with(sharedPref.edit()) {
                        putInt("COUNT", currentCount)
                        putLong("LAST_TIME", lastTimeMs)
                        apply()
                    }

                    if (currentCount >= maxLimit) {
                        Toast.makeText(context, "停損點到了！請審視這段關係。", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "紀錄成功，小石頭往前一格", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val remainingMinutes = (60 - (now - lastTimeMs) / 60000).toInt()
                    Toast.makeText(context, "太頻繁了！還在冷卻中。", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text(text = "我又內耗了 (+1)", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "上次紀錄: $lastTimeStr", fontSize = 14.sp, color = MaterialTheme.colorScheme.tertiary)
    }
}