package com.iborn.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

private val Lilac = Color(0xFFFFD700)
private val PaleLilac = Color(0xFF302B18)
private val Ink = Color(0xFFF4F4F4)
private val Muted = Color(0xFF969696)
private val AppBlack = Color(0xFF121212)
private val CardDark = Color(0xFF1E1E1E)
private val CardDarkAlt = Color(0xFF292929)
private fun s(en: String, fa: String, english: Boolean) = if (english) en else fa

data class ReminderItem(val id: String, val title: String, val date: String, val time: String, val category: String, val done: Boolean = false)
data class BatchItem(val id: String, val product: String, val barcode: String, val quantity: Int, val expiry: String, val category: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channel = NotificationChannel("reminders", "Reminder alerts", NotificationManager.IMPORTANCE_HIGH)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        setContent { ReminderApp(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ReminderApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("reminder_local", Context.MODE_PRIVATE) }
    var dark by remember {
        val needsDarkDefault = !prefs.getBoolean("dark_mode_v3_migrated", false)
        if (needsDarkDefault) prefs.edit().putBoolean("dark", true).putBoolean("dark_mode_v3_migrated", true).apply()
        mutableStateOf(prefs.getBoolean("dark", true))
    }
    var english by remember { mutableStateOf(prefs.getBoolean("english", true)) }
    var tab by remember { mutableIntStateOf(0) }
    var reminders by remember { mutableStateOf(loadReminders(prefs)) }
    var batches by remember { mutableStateOf(loadBatches(prefs)) }
    var productCategories by remember { mutableStateOf(loadCategories(prefs)) }
    var showReminder by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var reminderCategory by remember { mutableStateOf("Daily") }
    var barcode by remember { mutableStateOf("") }
    val scanner = remember { GmsBarcodeScanning.getClient(context) }
    val colors = if (dark) darkColorScheme(primary = Lilac, secondary = Lilac, background = AppBlack, surface = CardDark, surfaceVariant = CardDarkAlt, onBackground = Color(0xFFF5F5F5), onSurface = Color(0xFFF5F5F5)) else lightColorScheme(primary = Color(0xFF8A6A00), secondary = Color(0xFFB18A00), background = Color(0xFFF5F5F2), surface = Color.White, surfaceVariant = Color(0xFFEAEAEA), onBackground = Color(0xFF171717), onSurface = Color(0xFF171717))
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { BottomBar(tab, english) { tab = it } },
            floatingActionButton = {
                if (tab in 1..5) FloatingActionButton(onClick = { if (tab == 5) showBatch = true else { reminderCategory = listOf("", "Daily", "Installments", "Birthdays", "Miscellaneous")[tab]; showReminder = true } }, containerColor = Lilac, contentColor = Color.White) { Icon(Icons.Rounded.Add, contentDescription = s("Add", "افزودن", english)) }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Header(
                    dark, english,
                    { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() },
                    { english = !english; prefs.edit().putBoolean("english", english).apply() },
                    { tab = 1 },
                    {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Reminder — reminders and product expiry tracker")
                        }
                        context.startActivity(Intent.createChooser(share, s("Share Reminder", "اشتراک‌گذاری Reminder", english)))
                    }
                )
                AnimatedContent(targetState = tab, transitionSpec = { (fadeIn(tween(170)) + slideInHorizontally(tween(170)) { it / 14 }) togetherWith (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 18 }) }, label = "tab-transition") { page ->
                    when (page) {
                        0 -> HomeScreen(reminders, batches, english, { tab = 1 }, { tab = 5 }, { reminderCategory = "Daily"; showReminder = true })
                        1 -> ReminderList(reminders, english, { reminderCategory = "Daily"; showReminder = true }, { item -> reminders = reminders.map { if (it.id == item.id) it.copy(done = !it.done) else it }; saveReminders(prefs, reminders) }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        2 -> SimpleCategory(s("Installments", "اقساط", english), s("Payments and due dates", "پرداخت‌ها و سررسیدها", english), reminders.filter { it.category == "Installments" }, english, { reminderCategory = "Installments"; showReminder = true }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        3 -> SimpleCategory(s("Birthdays", "تولدها", english), s("Important dates", "تاریخ‌های مهم", english), reminders.filter { it.category == "Birthdays" }, english, { reminderCategory = "Birthdays"; showReminder = true }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        4 -> SimpleCategory(s("Miscellaneous", "متفرقه", english), s("Other reminders and notes", "یادآورها و یادداشت‌های دیگر", english), reminders.filter { it.category == "Miscellaneous" }, english, { reminderCategory = "Miscellaneous"; showReminder = true }, { item -> reminders = reminders.filterNot { it.id == item.id }; saveReminders(prefs, reminders) })
                        else -> ExpiryScreen(batches, productCategories, english, { showBatch = true }, { item -> batches = batches.filterNot { it.id == item.id }; saveBatches(prefs, batches) })
                    }
                }
            }
        }
        if (showReminder) ReminderDialog(reminderCategory, english, { showReminder = false }) { title, date, time, category ->
            val item = ReminderItem(UUID.randomUUID().toString(), title, date, time, category)
            reminders = reminders + item; saveReminders(prefs, reminders); scheduleReminder(context, item); showReminder = false
        }
        if (showBatch) BatchDialog(barcode, productCategories, english, onScan = { scanner.startScan().addOnSuccessListener { barcode = it.rawValue ?: "" } }, onDismiss = { showBatch = false }, onSave = { product, code, qty, expiry, category ->
            batches = batches + BatchItem(UUID.randomUUID().toString(), product, code, qty, expiry, category); saveBatches(prefs, batches); scheduleExpiry(context, product, expiry); if (!productCategories.contains(category)) { productCategories = productCategories + category; saveCategories(prefs, productCategories) }; showBatch = false; barcode = ""
        })
    }
}

@Composable private fun Header(
    dark: Boolean,
    english: Boolean,
    toggleTheme: () -> Unit,
    toggleLanguage: () -> Unit,
    openCalendar: () -> Unit,
    shareApp: () -> Unit
) {
    val today = remember { SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Calendar.getInstance().time) }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(39.dp).background(Lilac, CircleShape), contentAlignment = Alignment.Center) {
            Text("R", color = Color(0xFF171717), fontSize = 19.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s("TODAY", "امروز", english), color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Text("${s("Today, ", "امروز، ", english)}$today", color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { toggleLanguage() }) {
            Text(if (english) "EN" else "فا", modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp), color = Lilac, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(5.dp))
        IconButton(onClick = openCalendar) { Icon(Icons.Rounded.CalendarMonth, s("Calendar", "تقویم", english), tint = Muted, modifier = Modifier.size(21.dp)) }
        IconButton(onClick = toggleTheme) { Icon(Icons.Rounded.Settings, s("Appearance", "ظاهر", english), tint = Muted, modifier = Modifier.size(21.dp)) }
        IconButton(onClick = shareApp) { Icon(Icons.Rounded.Share, s("Share", "اشتراک‌گذاری", english), tint = Muted, modifier = Modifier.size(20.dp)) }
    }
}

@Composable private fun HomeScreen(
    reminders: List<ReminderItem>,
    batches: List<BatchItem>,
    english: Boolean,
    goReminders: () -> Unit,
    goExpiry: () -> Unit,
    add: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val active = remember(reminders) { reminders.count { !it.done } }
    val completed = remember(reminders) { reminders.count { it.done } }
    val expiring = remember(batches) { batches.count { daysUntil(it.expiry) in 0..7 } }
    val expired = remember(batches) { batches.count { daysUntil(it.expiry) < 0 } }
    val upcoming = remember(reminders, query) {
        reminders.filter { !it.done && (query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true)) }
            .sortedWith(compareBy<ReminderItem> { it.date }.thenBy { it.time }).take(4)
    }
    val soon = remember(batches, query) {
        batches.filter { (query.isBlank() || it.product.contains(query, true) || it.category.contains(query, true)) && daysUntil(it.expiry) >= 0 }
            .sortedBy { daysUntil(it.expiry) }.take(4)
    }
    val progress = if (reminders.isEmpty()) 0f else completed.toFloat() / reminders.size.toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 5.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(19.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = Muted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text(s("Search reminders or products", "جست‌وجوی یادآور یا محصول", english), color = Muted, fontSize = 13.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Close, s("Clear", "پاک کردن", english), tint = Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(s("YOUR SPACE", "نمای کلی", english), color = Lilac, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                        Text(s("Stay on top of today.", "امروز رو در دست بگیر.", english), color = MaterialTheme.colorScheme.onBackground, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { add() }) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, tint = Lilac, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s("New", "جدید", english), color = Lilac, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(s("TODAY'S OVERVIEW", "گزارش امروز", english), color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(active.toString(), color = MaterialTheme.colorScheme.onSurface, fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(s("active reminders", "یادآور فعال", english), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                                }
                            }
                            Box(Modifier.size(54.dp).border(1.dp, Lilac.copy(alpha = .35f), CircleShape).padding(5.dp).background(Lilac.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Bolt, null, tint = Lilac, modifier = Modifier.size(27.dp))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s("Completion", "انجام‌شده", english), color = Muted, fontSize = 10.sp)
                                Text("${(progress * 100).toInt()}%", color = Lilac, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = Lilac,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OverviewStat(s("Open", "باز", english), active.toString(), Icons.Rounded.NotificationsActive, Modifier.weight(1f))
                            OverviewStat(s("Expiring", "نزدیک انقضا", english), expiring.toString(), Icons.Rounded.Inventory2, Modifier.weight(1f))
                            OverviewStat(s("Expired", "منقضی", english), expired.toString(), Icons.Rounded.WarningAmber, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { HomeSectionHeading(s("Upcoming", "پیش رو", english), s("View all", "همه", english), goReminders) }
        if (upcoming.isEmpty()) {
            item { DarkEmptyState(s("Nothing coming up", "موردی برای نمایش نیست", english), s("Your next reminders will appear here.", "یادآورهای بعدی اینجا نمایش داده می‌شن.", english)) }
        } else {
            items(upcoming, key = { "home-reminder-${it.id}" }) { HomeReminderRow(it, english) }
        }
        item { HomeSectionHeading(s("Expiry watch", "مراقبت از انقضای کالا", english), s("Inventory", "محصولات", english), goExpiry) }
        if (soon.isEmpty()) {
            item { DarkEmptyState(s("No upcoming expiry", "انقضای نزدیکی ثبت نشده", english), s("Product batches approaching expiry will appear here.", "سری‌های نزدیک انقضا اینجا نمایش داده می‌شن.", english)) }
        } else {
            items(soon, key = { "home-batch-${it.id}" }) { HomeBatchRow(it, english) }
        }
    }
}

@Composable private fun OverviewStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = Lilac, modifier = Modifier.size(17.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Muted, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable private fun DarkEmptyState(title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Lilac.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EventAvailable, null, tint = Lilac, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun HomeSectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
            Text(action, color = Lilac, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ChevronRight, null, tint = Lilac, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable private fun HomeReminderRow(item: ReminderItem, english: Boolean) {
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Lilac.copy(alpha = .13f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.EventNote, null, tint = Lilac, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                Text("${item.date}  •  ${categoryLabel(item.category, english)}", color = Muted, fontSize = 10.sp, maxLines = 1)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                Text(item.time, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp), color = Lilac, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun HomeBatchRow(item: BatchItem, english: Boolean) {
    val days = daysUntil(item.expiry)
    val accent = if (days < 0) Color(0xFFFF6B6B) else if (days <= 7) Lilac else Color(0xFFB8B8B8)
    Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(accent.copy(alpha = .12f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Inventory2, null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.product, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                Text("${item.quantity} ${s("units", "عدد", english)}  •  ${item.expiry}", color = Muted, fontSize = 10.sp)
            }
            Text(if (days < 0) s("Expired", "منقضی", english) else if (days == 0) s("Today", "امروز", english) else "$days ${s("days", "روز", english)}", color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
    }
}

@Composable private fun ReminderList(list: List<ReminderItem>, english: Boolean, add: () -> Unit, toggle: (ReminderItem) -> Unit, delete: (ReminderItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { SectionTitle(s("Daily reminders", "یادآورهای روزانه", english), s("Tasks and appointments", "کارها و قرارهای مهمت", english), add); if (list.isEmpty()) EmptyState(s("Your list is empty", "لیستت خالیه", english), s("Add your first reminder", "اولین یادآورت رو ثبت کن", english)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(list, key = { it.id }) { ReminderRow(it, english, { toggle(it) }, { delete(it) }) } } }
}

@Composable private fun SimpleCategory(title: String, subtitle: String, list: List<ReminderItem>, english: Boolean, add: () -> Unit, delete: (ReminderItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) { SectionTitle(title, subtitle, add); if (list.isEmpty()) EmptyState(s("Nothing here yet", "هنوز موردی ثبت نشده", english), s("Tap + to add an item", "برای اضافه‌کردن روی + بزن", english)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list, key = { it.id }) { ReminderRow(it, english, {}, { delete(it) }) } } }
}

@Composable private fun ExpiryScreen(batches: List<BatchItem>, categories: List<String>, english: Boolean, add: () -> Unit, delete: (BatchItem) -> Unit) {
    val grouped = remember(batches) { batches.groupBy { it.category }.mapValues { (_, lots) -> lots.sortedBy { daysUntil(it.expiry) } } }
    val soonCount = remember(batches) { batches.count { daysUntil(it.expiry) in 0..7 } }
    val expiredCount = remember(batches) { batches.count { daysUntil(it.expiry) < 0 } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        SectionTitle(s("Product expiry", "انقضای محصولات", english), s("Track multiple batches for each product", "برای هر محصول چند سری ثبت کن", english), add)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 12.dp)) { MiniInfo(s("Batches", "کل سری‌ها", english), batches.size.toString(), Modifier.weight(1f)); MiniInfo(s("Soon", "نزدیک انقضا", english), soonCount.toString(), Modifier.weight(1f)); MiniInfo(s("Expired", "منقضی‌شده", english), expiredCount.toString(), Modifier.weight(1f)) }
        if (batches.isEmpty()) EmptyState(s("No products added", "هنوز محصولی ثبت نکردی", english), s("Add manually or scan a barcode", "محصول رو دستی وارد کن یا بارکدش رو اسکن کن", english)) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            grouped.forEach { (category, lots) -> item { Text(category, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Lilac, modifier = Modifier.padding(top = 9.dp, bottom = 2.dp)) }; items(lots, key = { it.id }) { BatchRow(it, english) { delete(it) } } }
        }
    }
}

@Composable private fun MiniInfo(label: String, value: String, modifier: Modifier = Modifier) { Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) { Column(Modifier.padding(11.dp)) { Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Lilac); Text(label, fontSize = 10.sp, color = Muted) } } }
@Composable private fun SectionTitle(title: String, subtitle: String, add: () -> Unit) { Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Text(subtitle, fontSize = 11.sp, color = Muted) }; Surface(shape = CircleShape, color = Lilac, modifier = Modifier.size(42.dp).clickable { add() }) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Color.White) } } } }

@Composable private fun ReminderRow(item: ReminderItem, english: Boolean, toggle: () -> Unit, delete: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(12.dp), color = PaleLilac, modifier = Modifier.size(42.dp).clickable { toggle() }) { Box(contentAlignment = Alignment.Center) { Icon(if (item.done) Icons.Rounded.CheckCircle else Icons.Rounded.Notifications, null, tint = Lilac) } }; Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface); Text("${item.date}  •  ${item.time}  •  ${categoryLabel(item.category, english)}", color = Muted, fontSize = 10.sp) }; IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) } } }
}
@Composable private fun BatchRow(item: BatchItem, english: Boolean, delete: () -> Unit) { val days = daysUntil(item.expiry); val warning = days < 0 || days <= 7; Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(12.dp), color = if (warning) Color(0xFF35251F) else PaleLilac, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Inventory2, null, tint = if (warning) Color(0xFFFF8A65) else Lilac) } }; Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(item.product, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface); Text("${item.quantity} ${s("units", "عدد", english)}  •  ${item.expiry}", fontSize = 11.sp, color = Muted); if (item.barcode.isNotBlank()) Text("Barcode: ${item.barcode}", fontSize = 9.sp, color = Muted) }; Text(if (days < 0) s("Expired", "منقضی", english) else if (days == 0) s("Today", "امروز", english) else "$days ${s("days", "روز", english)}", color = if (warning) Color(0xFFFF8A65) else Lilac, fontWeight = FontWeight.Bold, fontSize = 10.sp); IconButton(onClick = delete) { Icon(Icons.Rounded.DeleteOutline, null, tint = Muted) } } } }

@Composable private fun EmptyState(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().padding(vertical = 35.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(shape = CircleShape, color = PaleLilac, modifier = Modifier.size(62.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, tint = Lilac, modifier = Modifier.size(28.dp)) } }; Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground); Text(subtitle, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) } }

@Composable private fun BottomBar(selected: Int, english: Boolean, onSelect: (Int) -> Unit) {
    val entries = listOf(
        Triple(s("Home", "خانه", english), Icons.Rounded.Home, 0),
        Triple(s("Daily", "روزانه", english), Icons.Rounded.Notifications, 1),
        Triple(s("Bills", "اقساط", english), Icons.Rounded.Payments, 2),
        Triple(s("Birthdays", "تولدها", english), Icons.Rounded.Cake, 3),
        Triple(s("More", "متفرقه", english), Icons.Rounded.MoreHoriz, 4),
        Triple(s("Expiry", "انقضا", english), Icons.Rounded.Inventory2, 5)
    )
    NavigationBar(
        modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).border(width = 0.6.dp, color = Color(0xFF303030)),
        containerColor = AppBlack,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        entries.forEach { entry ->
            NavigationBarItem(
                selected = selected == entry.third,
                onClick = { onSelect(entry.third) },
                icon = { Icon(entry.second, contentDescription = entry.first, modifier = Modifier.size(if (selected == entry.third) 25.dp else 22.dp)) },
                label = { Text(entry.first, fontSize = 9.sp, maxLines = 1, fontWeight = if (selected == entry.third) FontWeight.Bold else FontWeight.Medium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Lilac,
                    selectedTextColor = Lilac,
                    unselectedIconColor = Color(0xFF858585),
                    unselectedTextColor = Color(0xFF858585),
                    indicatorColor = Lilac.copy(alpha = .12f)
                )
            )
        }
    }
}

@Composable private fun ReminderDialog(initialCategory: String, english: Boolean, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf("1405/09/01") }; var time by remember { mutableStateOf("09:00") }; var category by remember { mutableStateOf(initialCategory) }; var picker by remember { mutableStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(s("New reminder", "یادآور جدید", english), fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text(s("Title", "عنوان یادآور", english)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("$date") }
        OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Schedule, null); Spacer(Modifier.width(8.dp)); Text(time) }
        Text(s("Category", "دسته‌بندی", english), color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("Daily", "Installments", "Birthdays", "Miscellaneous").forEach { FilterChip(category == it, { category = it }, label = { Text(categoryLabel(it, english), fontSize = 10.sp) }) } }
    } }, confirmButton = { TextButton(onClick = { if (title.isNotBlank() && validJalali(date)) onSave(title, date, time, category) }) { Text(s("Save", "ذخیره", english), color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(s("Cancel", "انصراف", english)) } })
    if (picker != 0) WheelPickerDialog(picker == 1, date, time, english, { date = it; picker = 0 }, { time = it; picker = 0 }, { picker = 0 })
}

@Composable private fun WheelPickerDialog(isDate: Boolean, date: String, time: String, english: Boolean, onDate: (String) -> Unit, onTime: (String) -> Unit, onDismiss: () -> Unit) {
    val dp = date.split('/'); val tp = time.split(':'); var y by remember { mutableIntStateOf(dp.getOrNull(0)?.toIntOrNull() ?: 1405) }; var m by remember { mutableIntStateOf(dp.getOrNull(1)?.toIntOrNull() ?: 9) }; var d by remember { mutableIntStateOf(dp.getOrNull(2)?.toIntOrNull() ?: 1) }; var h by remember { mutableIntStateOf(tp.getOrNull(0)?.toIntOrNull() ?: 9) }; var min by remember { mutableIntStateOf(tp.getOrNull(1)?.toIntOrNull() ?: 0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (isDate) s("Choose date", "انتخاب تاریخ", english) else s("Choose time", "انتخاب ساعت", english)) }, text = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (isDate) { Wheel(y, 1400, 1450) { y = it }; Wheel(m, 1, 12) { m = it; d = d.coerceAtMost(if (m <= 6) 31 else 30) }; Wheel(d, 1, if (m <= 6) 31 else 30) { d = it } }
            else { Wheel(h, 0, 23) { h = it }; Text(":", fontSize = 22.sp, fontWeight = FontWeight.Bold); Wheel(min, 0, 59) { min = it } }
        }
    }, confirmButton = { TextButton(onClick = { if (isDate) onDate("%04d/%02d/%02d".format(y,m,d)) else onTime("%02d:%02d".format(h,min)) }) { Text(s("Done", "تأیید", english), color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(s("Cancel", "انصراف", english)) } })
}
@Composable private fun Wheel(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) { AndroidView(factory = { ctx -> NumberPicker(ctx).apply { minValue = min; maxValue = max; this.value = value; wrapSelectorWheel = true; descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS; setOnValueChangedListener { _, _, new -> onChange(new) } } }, update = { if (it.value != value) it.value = value }, modifier = Modifier.width(76.dp).height(145.dp)) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun BatchDialog(initialBarcode: String, categories: List<String>, english: Boolean, onScan: () -> Unit, onDismiss: () -> Unit, onSave: (String, String, Int, String, String) -> Unit) {
    var product by remember { mutableStateOf("") }; var code by remember { mutableStateOf(initialBarcode) }; var qty by remember { mutableStateOf("1") }; var expiry by remember { mutableStateOf("1405/09/01") }; var category by remember { mutableStateOf(categories.firstOrNull() ?: "General") }; var newCategory by remember { mutableStateOf("") }; var picker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(s("Add product batch", "ثبت سری محصول", english), fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        OutlinedTextField(product, { product = it }, label = { Text(s("Product name", "نام محصول", english)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(code, { code = it }, label = { Text(s("Barcode (optional)", "بارکد (اختیاری)", english)) }, singleLine = true, trailingIcon = { IconButton(onClick = onScan) { Icon(Icons.Rounded.QrCodeScanner, s("Scan barcode", "اسکن بارکد", english), tint = Lilac) } })
        OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text(s("Quantity", "تعداد", english)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        Text(s("Product category", "دسته‌بندی محصول", english), color = Muted, fontSize = 12.sp)
        if (categories.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { categories.forEach { FilterChip(category == it, { category = it }, label = { Text(it, fontSize = 10.sp) }) } }
        OutlinedTextField(newCategory, { newCategory = it; if (it.isNotBlank()) category = it.trim() }, label = { Text(s("Add a new category", "دسته‌بندی جدید", english)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text("${s("Expiry date", "تاریخ انقضا", english)}: $expiry") }
        Text(s("Each batch keeps its own expiry date. Categories are saved for next time.", "هر سری تاریخ انقضای مستقل دارد و دسته‌بندی برای دفعات بعد ذخیره می‌شود.", english), color = Muted, fontSize = 10.sp)
    } }, confirmButton = { TextButton(onClick = { val count = qty.toIntOrNull() ?: 0; val cat = category.trim().ifBlank { "General" }; if (product.isNotBlank() && count > 0 && validJalali(expiry)) onSave(product.trim(), code, count, expiry, cat) }) { Text(s("Save batch", "ثبت سری", english), color = Lilac) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(s("Cancel", "انصراف", english)) } })
    if (picker) WheelPickerDialog(true, expiry, "09:00", english, { expiry = it; picker = false }, {}, { picker = false })
}

private fun categoryLabel(category: String, english: Boolean) = when (category) { "Daily" -> s("Daily", "روزانه", english); "Installments" -> s("Installments", "اقساط", english); "Birthdays" -> s("Birthdays", "تولدها", english); "Miscellaneous" -> s("Miscellaneous", "متفرقه", english); else -> category }
private fun saveReminders(p: android.content.SharedPreferences, list: List<ReminderItem>) { val a=JSONArray(); list.forEach { a.put(JSONObject().put("id",it.id).put("title",it.title).put("date",it.date).put("time",it.time).put("category",it.category).put("done",it.done)) }; p.edit().putString("reminders",a.toString()).apply() }
private fun loadReminders(p: android.content.SharedPreferences): List<ReminderItem> = try { val a=JSONArray(p.getString("reminders","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); ReminderItem(o.optString("id"),o.optString("title"),o.optString("date"),o.optString("time"),o.optString("category"),o.optBoolean("done")) } } catch (_:Exception) { emptyList() }
private fun saveBatches(p: android.content.SharedPreferences, list: List<BatchItem>) { val a=JSONArray(); list.forEach { a.put(JSONObject().put("id",it.id).put("product",it.product).put("barcode",it.barcode).put("quantity",it.quantity).put("expiry",it.expiry).put("category",it.category)) }; p.edit().putString("batches",a.toString()).apply() }
private fun loadBatches(p: android.content.SharedPreferences): List<BatchItem> = try { val a=JSONArray(p.getString("batches","[]")); (0 until a.length()).map { val o=a.getJSONObject(it); BatchItem(o.optString("id"),o.optString("product"),o.optString("barcode"),o.optInt("quantity"),o.optString("expiry"),o.optString("category","General")) } } catch (_:Exception) { emptyList() }
private fun saveCategories(p: android.content.SharedPreferences, list: List<String>) { p.edit().putString("product_categories", JSONArray(list).toString()).apply() }
private fun loadCategories(p: android.content.SharedPreferences): List<String> = try { val a=JSONArray(p.getString("product_categories","[]")); (0 until a.length()).map { a.getString(it) } } catch (_:Exception) { emptyList() }
private fun validJalali(s:String):Boolean { val p=s.split('/'); if(p.size!=3)return false; val y=p[0].toIntOrNull()?:return false; val m=p[1].toIntOrNull()?:return false; val d=p[2].toIntOrNull()?:return false; return y in 1300..1500 && m in 1..12 && d in 1..if(m<=6)31 else if(m<=11)30 else 30 }
private fun daysUntil(j:String):Int = try { val p=j.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val target=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],0,0,0);set(Calendar.MILLISECOND,0) }; val today=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }; ((target.timeInMillis-today.timeInMillis)/86400000L).toInt() } catch (_:Exception) { 9999 }
private fun jalaliToGregorian(jy:Int,jm:Int,jd:Int):IntArray { var year=jy+1595; var days=-355668+365*year+(year/33)*8+(year%33+3)/4+jd+(if(jm<7)(jm-1)*31 else (jm-7)*30+186); var gy=400*(days/146097); days%=146097; if(days>36524){gy+=100*((--days)/36524);days%=36524;if(days>=365)days++}; gy+=4*(days/1461); days%=1461; if(days>365){gy+=(days-1)/365;days=(days-1)%365}; var gd=days+1; val leap=(gy%4==0&&gy%100!=0)||gy%400==0; val md=intArrayOf(31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31); var gm=0; while(gm<12&&gd>md[gm]){gd-=md[gm];gm++}; return intArrayOf(gy,gm+1,gd) }
private fun scheduleReminder(c:Context,i:ReminderItem) { try { val p=i.date.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val t=i.time.split(':'); val cal=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],t[0].toInt(),t[1].toInt(),0);set(Calendar.MILLISECOND,0) }; schedule(c,i.id.hashCode(),i.title,cal.timeInMillis) } catch (_:Exception) {} }
private fun scheduleExpiry(c:Context,product:String,expiry:String) { try { val p=expiry.split('/'); val g=jalaliToGregorian(p[0].toInt(),p[1].toInt(),p[2].toInt()); val cal=Calendar.getInstance().apply { set(g[0],g[1]-1,g[2],9,0,0);set(Calendar.MILLISECOND,0) }; val base=product.hashCode()+expiry.hashCode(); schedule(c,base-7,"Reminder: $product expires in 7 days",cal.timeInMillis-7*86400000L); schedule(c,base-1,"Reminder: $product expires tomorrow",cal.timeInMillis-86400000L); schedule(c,base,"Product expiry: $product",cal.timeInMillis) } catch (_:Exception) {} }
private fun schedule(c:Context,id:Int,title:String,at:Long) { if(at<System.currentTimeMillis())return; val i=Intent(c,ReminderReceiver::class.java).putExtra("title",title); val pi=PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val am=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager; if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms())am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) }
class ReminderReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val title=i.getStringExtra("title")?:"Reminder";val n=NotificationCompat.Builder(c,"reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Reminder").setContentText(title).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build();val m=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;if(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)m.notify(title.hashCode(),n)}}
class BootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(i.action==Intent.ACTION_BOOT_COMPLETED){val p=c.getSharedPreferences("reminder_local",Context.MODE_PRIVATE);loadReminders(p).filter{!it.done}.forEach{scheduleReminder(c,it)};loadBatches(p).forEach{scheduleExpiry(c,it.product,it.expiry)}}}}
