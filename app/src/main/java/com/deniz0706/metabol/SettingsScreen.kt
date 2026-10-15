package com.deniz0706.metabol

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SBack = Color(0xFF080D15)
private val SCard = Color(0xFF131C2A)
private val SMint = Color(0xFF71E8C4)
private val SText = Color(0xFFF4F8FC)
private val SMuted = Color(0xFF8B9CB2)
private val preferenceNames = listOf("metabol_local", "metabol_nutrition", "metabol_body")

internal fun createBackup(context: Context): String {
    val root = JSONObject()
    root.put("format", "METABOL-backup")
    root.put("version", 1)
    root.put("createdAt", System.currentTimeMillis())
    val data = JSONObject()
    preferenceNames.forEach { name ->
        val group = JSONObject()
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (value is String) group.put(key, value)
        }
        data.put(name, group)
    }
    root.put("data", data)
    return root.toString(2)
}

internal fun restoreBackup(context: Context, source: String): Boolean {
    val root = JSONObject(source)
    if (root.optString("format") != "METABOL-backup" || root.optInt("version") != 1) return false
    val data = root.getJSONObject("data")
    // Validate all groups and their JSON payloads before modifying any stored data.
    val prepared = preferenceNames.associateWith { name ->
        val group = data.getJSONObject(name)
        val entries = mutableMapOf<String, String>()
        val iterator = group.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = group.getString(key)
            if (value.length > 2_000_000) return false
            if (key == "weights" || key == "records" || key.startsWith("meals_")) {
                org.json.JSONArray(value)
            }
            entries[key] = value
        }
        entries
    }
    prepared.forEach { (name, entries) ->
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        entries.forEach { (key, value) -> editor.putString(key, value) }
        editor.commit()
    }
    return true
}

@Composable
internal fun SettingsScreen(context: Context, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(createBackup(context).toByteArray(Charsets.UTF_8))
                } ?: error("Dosya açılamadı")
                "Yedek dosyası oluşturuldu."
            }.getOrElse { "Dışa aktarma başarısız: ${it.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            message = runCatching {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Dosya okunamadı")
                if (content.length > 5_000_000) error("Dosya çok büyük")
                val json = JSONObject(content)
                if (json.optString("format") != "METABOL-backup" || json.optInt("version") != 1) {
                    error("Geçersiz METABOL yedeği")
                }
                pendingImport = content
                "Yedek doğrulandı. İçe aktarmayı onayla."
            }.getOrElse { "İçe aktarma başarısız: ${it.message}" }
        }
    }
    LazyColumn(modifier.fillMaxSize().background(SBack), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("M E T A B O L", color = SMint, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(24.dp))
            Text("AYARLAR", color = SMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Verilerin kontrolü sende.", color = SText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Column(Modifier.fillMaxWidth().background(SCard, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Yedekleme ve geri yükleme", color = SText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Kilo, vücut ölçümleri ve beslenme kayıtlarını JSON olarak dışa aktar. İnternet gerekmez.",
                    color = SMuted, fontSize = 13.sp)
                Button(onClick = {
                    val name = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("metabol-backup-$name.json")
                }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SMint, contentColor = SBack)) {
                    Text("Yedeği dışa aktar")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Yedekten geri yükle", color = SMint) }
                Text("Geri yükleme mevcut kayıtların yerine geçer. Önce yedek alman önerilir.",
                    color = SMuted, fontSize = 12.sp)
            }
        }
        if (message.isNotBlank()) item {
            Text(message, color = SMint, fontSize = 13.sp)
        }
        item {
            Column(Modifier.fillMaxWidth().background(SCard, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gizlilik", color = SText, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("Bu sürümde kayıtlar cihazda tutulur. Hesap veya sunucu senkronizasyonu yoktur.",
                    color = SMuted, fontSize = 13.sp)
                Text("METABOL · Developed by Deniz", color = SMint, fontSize = 12.sp)
            }
        }
    }
    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = SCard,
            title = { Text("Mevcut kayıtlar değiştirilecek", color = SText) },
            text = { Text("Yedekteki veriler cihazdaki METABOL kayıtlarının yerini alacak. İşlemden sonra uygulamayı yeniden aç.", color = SMuted) },
            confirmButton = {
                TextButton(onClick = {
                    message = runCatching {
                        if (restoreBackup(context, pendingImport!!)) "Yedek geri yüklendi. Uygulamayı kapatıp yeniden aç."
                        else "Geçersiz yedek dosyası."
                    }.getOrElse { "Geri yükleme başarısız: ${it.message}" }
                    pendingImport = null
                }) { Text("Geri yükle", color = SMint) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("İptal") } }
        )
    }
}
