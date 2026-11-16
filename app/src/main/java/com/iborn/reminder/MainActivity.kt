package com.iborn.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Lilac = Color(0xFF8066C8)
private val PaleLilac = Color(0xFFF0ECFA)
private val Ink = Color(0xFF29243A)
private val Muted = Color(0xFF898397)

 data class ReminderItem(val id: String, val title: String, val date: String, val time: String, val category: String, val priority: String, val done: Boolean = false)
 data class BatchItem(val id: String, val product: String, val barcode: String, val quantity: Int, val expiry: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        }
        setContent { ReminderApp(this) }
    }

    private fun createChannel() {
        val channel = NotificationChannel("reminders", "یادآورها و انقضای محصولات", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

@Composable
private fun ReminderApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("reminder_local", Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var tab by remember { mutableIntStateOf(0) }
    var reminders by remember { mutableStateOf(loadReminders(prefs)) }
    var batches by remember { mutableStateOf(loadBatches(prefs)) }
    var showReminder by remember { mutableStateOf(false) }
    var reminderCategory by remember { mutableStateOf("روزانه") }
    var showBatch by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf("") }
    val scanner = remember { GmsBarcodeScanning.getClient(context) }
    val light = lightColors()
    val darkColors = darkColors()
    MaterialTheme(colorScheme = if (dark) darkColors else light) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Header(dark, { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() })
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    when (tab) {
                        0 -> HomeScreen(reminders, batches, { tab = 1 }, { tab = 5 }, { reminderCategory = "روزانه"; showReminder = true })
                        1 -> ReminderList(reminders, { reminderCategory = "روزانه"; showReminder = true }, { item ->
                            reminders = reminders.map { if (it.id == item.id) it.copy(done = !it.done) else it }; saveReminders(prefs, reminders)
                        }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        2 -> SimpleCategory("اقساط", "پرداخت‌ها و سررسیدهای مالی", reminders.filter { it.category == "اقساط" }, { reminderCategory = "اقساط"; showReminder = true })
                        3 -> SimpleCategory("تولدها", "تاریخ‌های تولد و مناسبت‌ها", reminders.filter { it.category == "تولدها" }, { reminderCategory = "تولدها"; showReminder = true })
                        4 -> SimpleCategory("متفرقه", "یادداشت‌ها و یادآورهای دیگر", reminders.filter { it.category == "متفرقه" }, { reminderCategory = "متفرقه"; showReminder = true })
                        else -> ExpiryScreen(batches, { showBatch = true }, { item -> batches = batches.filterNot { it.id == item.id }; saveBatches(prefs, batches) })
                    }
                }
                Spacer(Modifier.weight(1f))
                BottomBar(tab) { tab = it }
            }
        }
        if (showReminder) ReminderDialog(initialCategory = reminderCategory, onDismiss = { showReminder = false }, onSave = { title, date, time, category, priority ->
            val item = ReminderItem(UUID.randomUUID().toString(), title, date, time, category, priority)
            reminders = reminders + item; saveReminders(prefs, reminders); scheduleReminder(context, item); showReminder = false
        })
        if (showBatch) BatchDialog(initialBarcode = barcode, onScan = {
            scanner.startScan().addOnSuccessListener { result -> barcode = result.rawValue ?: "" }
        }, onDismiss = { showBatch = false }, onSave = { product, code, qty, expiry ->
            batches = batches + BatchItem(UUID.randomUUID().toString(), product, code, qty, expiry); saveBatches(prefs, batches); scheduleExpiry(context, product, expiry); showBatch = false; barcode = ""
        })
    }
}

@Composable private fun Header(dark: Boolean, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("سلام، خوش اومدی", color = Muted, fontSize = 12.sp)
            Text("Reminder", color = MaterialTheme.colorScheme.onBackground, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        }
        Surface(shape = CircleShape, color = if (dark) Color(0xFF383044) else PaleLilac, modifier = Modifier.size(44.dp).clickable { toggle() }) {
            Box(contentAlignment = Alignment.Center) { Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, null, tint = Lilac) }
        }
    }
}

@Composable private fun HomeScreen(reminders: List<ReminderItem>, batches: List<BatchItem>, goReminders: () -> Unit, goExpiry: () -> Unit, add: () -> Unit) {
    val active = reminders.count { !it.done }
    val expiring = batches.count { daysUntil(it.expiry) in 0..7 }
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = Lilac, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("امروز رو مرتب‌تر بساز", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp)); Text("همه‌چیز، سرِ وقت.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("$active", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold); Text("کار باقی‌مانده", color = Color.White.copy(alpha = .8f), fontSize = 12.sp) }
                        FilledTonalButton(onClick = add, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Lilac)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("یادآور جدید") }
                    }
                }
            }
        }
        item { Text("نمای کلی", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("یادآورهای فعال", active.toString(), Icons.Rounded.NotificationsActive, Modifier.weight(1f), goReminders)
                StatCard("انقضای نزدیک", expiring.toString(), Icons.Rounded.Inventory2, Modifier.weight(1f), goExpiry)
            }
        }
        item { Text("یادآورهای پیش رو", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground) }
        val upcoming = reminders.filter { !it.done }.take(4)
        if (upcoming.isEmpty()) item { EmptyState("هنوز یادآوری ثبت نکردی", "با دکمه یادآور جدید شروع کن") }
        items(upcoming) { ReminderRow(it, {}, {}) }
        item { Text("محصولات با انقضای نزدیک", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground) }
        val soon = batches.filter { daysUntil(it.expiry) in 0..7 }.take(3)
        if (soon.isEmpty()) item { EmptyState("فعلاً محصولی نزدیک انقضا نیست", "وضعیت سری‌های کالا از اینجا قابل پیگیریه") }
        items(soon) { BatchRow(it, {}) }
    }
}

@Composable private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, click: () -> Unit) {
    Surface(modifier.clickable { click() }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(Modifier.padding(17.dp)) { Icon(icon, null, tint = Lilac); Spacer(Modifier.height(12.dp)); Text(value, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text(title, color = Muted, fontSize = 12.sp) }
    }
}

@Composable private fun ReminderList(remindersList: List<ReminderItem>, add: () -> Unit, toggle: (ReminderItem) -> Unit, delete: (ReminderItem) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SectionTitle("یادآورهای روزانه", "کارها و قرارهای مهمت") { add() }
        if (remindersList.isEmpty()) EmptyState("لیستت خالیه", "اولین یادآورت رو ثبت کن")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(remindersList) { ReminderRow(it, { toggle(it) }, { delete(it) }) } }
    }
}

@Composable private fun SimpleCategory(title: String, subtitle: String, categoryItems: List<ReminderItem>, add: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SectionTitle(title, subtitle) { add() }
        if (categoryItems.isEmpty()) EmptyState("هنوز موردی ثبت نشده", "برای اضافه‌کردن روی + بزن")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(categoryItems) { ReminderRow(it, {}, {}) } }
    }
}

@Composable private fun ExpiryScreen(batches: List<BatchItem>, add: () -> Unit, delete: (BatchItem) -> Unit) {
    val grouped = batches.groupBy { it.product }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SectionTitle("انقضای محصولات", "هر محصول می‌تونه چند سری با تاریخ متفاوت داشته باشه") { add() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            MiniInfo("کل سری‌ها", batches.size.toString(), Modifier.weight(1f)); MiniInfo("نزدیک انقضا", batches.count { daysUntil(it.expiry) in 0..7 }.toString(), Modifier.weight(1f)); MiniInfo("منقضی‌شده", batches.count { daysUntil(it.expiry) < 0 }.toString(), Modifier.weight(1f))
        }
        if (batches.isEmpty()) EmptyState("هنوز محصولی ثبت نکردی", "محصول رو دستی وارد کن یا بارکدش رو اسکن کن")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            grouped.forEach { (name, lots) ->
                item { Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp, bottom = 1.dp)) }
                items(lots.sortedBy { daysUntil(it.expiry) }) { BatchRow(it, { delete(it) }) }
            }
        }
    }
}

@Composable private fun MiniInfo(label: String, value: String, modifier: Modifier = Modifier) { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) { Column(Modifier.padding(11.dp)) { Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Lilac); Text(label, fontSize = 10.sp, color = Muted) } } }

@Composable private fun SectionTitle(title: String, subtitle: String, add: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Text(subtitle, fontSize = 11.sp, color = Muted) }
        Surface(shape = CircleShape, color = Lilac, modifier = Modifier.size(44.dp).clickable { add() }) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White) } }
    }
}

@Composable private fun ReminderRow(item: ReminderItem, toggle: () -> Unit, delete: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = PaleLilac, modifier = Modifier.size(42.dp).clickable { toggle() }) { Box(contentAlignment = Alignment.Center) { Icon(if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications, null, tint = Lilac) } }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface); Text("${item.date}  •  ${item.time}  •  ${item.category}", color = Muted, fontSize = 10.sp) }
            Text(item.priority, color = if (item.priority == "زیاد") Color(0xFFE27B75) else Muted, fontSize = 10.sp)
            IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) }
        }
    }
}

@Composable private fun BatchRow(item: BatchItem, delete: () -> Unit) {
    val days = daysUntil(item.expiry)
    val warning = days < 0 || days <= 7
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (warning) Color(0xFFFFEEE9) else PaleLilac, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Inventory2, null, tint = if (warning) Color(0xFFD77B67) else Lilac) } }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text("${item.quantity} عدد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("انقضا: ${item.expiry}", fontSize = 11.sp, color = Muted); if (item.barcode.isNotBlank()) Text("بارکد: ${item.barcode}", fontSize = 9.sp, color = Muted) }
            Text(if (days < 0) "منقضی" else if (days == 0) "امروز" else "$days روز", color = if (warning) Color(0xFFD77B67) else Lilac, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) }
        }
    }
}

@Composable private fun EmptyState(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().padding(vertical = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(shape = CircleShape, color = PaleLilac, modifier = Modifier.size(62.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Lilac, modifier = Modifier.size(28.dp)) } }; Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground); Text(subtitle, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) } }

@Composable private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val entries = listOf("خانه" to Icons.Rounded.Home, "روزانه" to Icons.Rounded.Notifications, "اقساط" to Icons.Rounded.Payments, "تولدها" to Icons.Rounded.Cake, "متفرقه" to Icons.Rounded.MoreHoriz, "انقضا" to Icons.Rounded.Inventory2)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        entries.forEachIndexed { index, pair -> NavigationBarItem(selected = selected == index, onClick = { onSelect(index) }, icon = { Icon(pair.second, null) }, label = { Text(pair.first, fontSize = 8.sp, maxLines = 1) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Lilac, indicatorColor = PaleLilac, selectedTextColor = Lilac)) }
    }
}

@Composable private fun ReminderDialog(initialCategory: String, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf("1405/09/01") }; var time by remember { mutableStateOf("09:00") }; var category by remember { mutableStateOf(initialCategory) }; var priority by remember { mutableStateOf("معمولی") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("یادآور جدید", fontWeight = FontWeight.Bold) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("عنوان یادآور") }, singleLine = true)
            OutlinedTextField(date, { date = it }, label = { Text("تاریخ شمسی (۱۴۰۵/۰۹/۰۱)") }, singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("ساعت (09:00)") }, singleLine = true)
            Text("دسته‌بندی", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("روزانه", "اقساط", "تولدها", "متفرقه").forEach { FilterChip(category == it, { category = it }, label = { Text(it, fontSize = 10.sp) }) } }
            Text("اولویت", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("کم", "معمولی", "زیاد").forEach { FilterChip(priority == it, { priority = it }, label = { Text(it) }) } }
        }
    }, confirmButton = { TextButton(onClick = { if (title.isNotBlank() && validJalali(date) && validTime(time)) onSave(title, date, time, category, priority) }) { Text("ذخیره", color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable private fun BatchDialog(initialBarcode: String, onScan: () -> Unit, onDismiss: () -> Unit, onSave: (String, String, Int, String) -> Unit) {
    var product by remember { mutableStateOf("") }; var code by remember { mutableStateOf(initialBarcode) }; var qty by remember { mutableStateOf("1") }; var expiry by remember { mutableStateOf("1405/09/01") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ثبت سری محصول", fontWeight = FontWeight.Bold) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(product, { product = it }, label = { Text("نام محصول") }, singleLine = true)
            OutlinedTextField(code, { code = it }, label = { Text("بارکد (اختیاری)") }, singleLine = true, trailingIcon = { IconButton(onClick = onScan) { Icon(Icons.Rounded.QrCodeScanner, "اسکن بارکد", tint = Lilac) } })
            OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text("تعداد") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(expiry, { expiry = it }, label = { Text("تاریخ انقضا شمسی") }, singleLine = true)
            Text("برای یک محصول می‌تونی چند سری با تاریخ‌های مختلف ثبت کنی.", color = Muted, fontSize = 11.sp)
        }
    }, confirmButton = { TextButton(onClick = { val count = qty.toIntOrNull() ?: 0; if (product.isNotBlank() && count > 0 && validJalali(expiry)) onSave(product, code, count, expiry) }) { Text("ثبت سری", color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

private fun lightColors() = lightColorScheme(primary = Lilac, secondary = Color(0xFFB4A1E4), background = Color(0xFFF8F7FB), surface = Color.White, onBackground = Ink, onSurface = Ink)
private fun darkColors() = darkColorScheme(primary = Color(0xFFB9A5F0), secondary = Lilac, background = Color(0xFF17151D), surface = Color(0xFF25212D), onBackground = Color(0xFFF4F0FA), onSurface = Color(0xFFF4F0FA))

private fun saveReminders(prefs: android.content.SharedPreferences, items: List<ReminderItem>) { val a = JSONArray(); items.forEach { a.put(JSONObject().put("id",it.id).put("title",it.title).put("date",it.date).put("time",it.time).put("category",it.category).put("priority",it.priority).put("done",it.done)) }; prefs.edit().putString("reminders",a.toString()).apply() }
private fun loadReminders(prefs: android.content.SharedPreferences): List<ReminderItem> = try { val a=JSONArray(prefs.getString("reminders","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); ReminderItem(o.optString("id"),o.optString("title"),o.optString("date"),o.optString("time"),o.optString("category"),o.optString("priority"),o.optBoolean("done")) } } catch (_:Exception) { emptyList() }
private fun saveBatches(prefs: android.content.SharedPreferences, items: List<BatchItem>) { val a=JSONArray(); items.forEach { a.put(JSONObject().put("id",it.id).put("product",it.product).put("barcode",it.barcode).put("quantity",it.quantity).put("expiry",it.expiry)) }; prefs.edit().putString("batches",a.toString()).apply() }
private fun loadBatches(prefs: android.content.SharedPreferences): List<BatchItem> = try { val a=JSONArray(prefs.getString("batches","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); BatchItem(o.optString("id"),o.optString("product"),o.optString("barcode"),o.optInt("quantity"),o.optString("expiry")) } } catch (_:Exception) { emptyList() }

private fun validTime(s:String)= Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$").matches(s)
private fun validJalali(s:String):Boolean { val p=s.split('/'); if(p.size!=3) return false; val y=p[0].toIntOrNull()?:return false; val m=p[1].toIntOrNull()?:return false; val d=p[2].toIntOrNull()?:return false; return y in 1300..1500 && m in 1..12 && d in 1..if(m<=6)31 else if(m<=11)30 else 30 }
private fun daysUntil(jalali:String):Int { return try { val p=jalali.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val target=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],0,0,0); set(Calendar.MILLISECOND,0) }; val today=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }; ((target.timeInMillis-today.timeInMillis)/86400000L).toInt() } catch (_:Exception) { 9999 } }
private fun jalaliToGregorian(jy:Int,jm:Int,jd:Int):IntArray { var year=jy+1595; var days=-355668+365*year+(year/33)*8+(year%33+3)/4+jd+(if(jm<7)(jm-1)*31 else (jm-7)*30+186); var gy=400*(days/146097); days%=146097; if(days>36524){gy+=100*((--days)/36524);days%=36524;if(days>=365)days++}; gy+=4*(days/1461); days%=1461; if(days>365){gy+=(days-1)/365;days=(days-1)%365}; var gd=days+1; val leap=(gy%4==0 && gy%100!=0)||gy%400==0; val monthDays=intArrayOf(31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31); var gm=0; while(gm<12 && gd>monthDays[gm]){gd-=monthDays[gm];gm++}; return intArrayOf(gy,gm+1,gd) }

private fun scheduleReminder(context: Context, item: ReminderItem) { try { val p=item.date.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val t=item.time.split(':'); val cal=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],t[0].toInt(),t[1].toInt(),0);set(Calendar.MILLISECOND,0) }; schedule(context,item.id.hashCode(),item.title,cal.timeInMillis) } catch (_:Exception) {} }
private fun scheduleExpiry(context: Context, product: String, expiry: String) { try { val p=expiry.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val cal=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],9,0,0);set(Calendar.MILLISECOND,0) }; val base=product.hashCode()+expiry.hashCode(); schedule(context,base-7,"هشدار: $product تا ۷ روز دیگر منقضی می‌شود",cal.timeInMillis-7*86400000L); schedule(context,base-1,"هشدار: $product فردا منقضی می‌شود",cal.timeInMillis-86400000L); schedule(context,base,"امروز تاریخ انقضای $product است",cal.timeInMillis) } catch (_:Exception) {} }
private fun schedule(context: Context, id:Int, title:String, at:Long) { if(at<System.currentTimeMillis()) return; val intent=Intent(context,ReminderReceiver::class.java).putExtra("title",title); val pi=PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val am=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager; if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }

class ReminderReceiver: BroadcastReceiver() { override fun onReceive(context:Context,intent:Intent) { val title=intent.getStringExtra("title")?:"یادآور"; val n=NotificationCompat.Builder(context,"reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Reminder").setContentText(title).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build(); val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager; if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) manager.notify(title.hashCode(),n) } }
class BootReceiver: BroadcastReceiver() { override fun onReceive(context:Context,intent:Intent) { if (intent.action == Intent.ACTION_BOOT_COMPLETED) { val prefs=context.getSharedPreferences("reminder_local",Context.MODE_PRIVATE); loadReminders(prefs).filter { !it.done }.forEach { scheduleReminder(context,it) }; loadBatches(prefs).forEach { scheduleExpiry(context,it.product,it.expiry) } } } }
