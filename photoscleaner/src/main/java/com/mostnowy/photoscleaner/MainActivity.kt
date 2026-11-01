package com.mostnowy.photoscleaner

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class PhotoItem(val uri: Uri, val name: String, val size: Long, val date: Long, val hash: String)
data class DuplicateGroup(val hash: String, val items: List<PhotoItem>)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { CleanerScreen() } }
  }
}

@Composable
private fun CleanerScreen() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val activity = context as ComponentActivity
  val scope = rememberCoroutineScope()
  var scanning by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf("Gotowy do skanowania zdjęć dostępnych na tym urządzeniu.") }
  var groups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
  var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
  var scanned by remember { mutableIntStateOf(0) }

  val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
    status = if (result.resultCode == android.app.Activity.RESULT_OK) "Android potwierdził przeniesienie wybranych zdjęć do kosza." else "Operacja została anulowana."
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      selected = emptySet()
      groups = emptyList()
      status += " Uruchom skanowanie ponownie, aby odświeżyć wyniki."
    }
  }

  fun requestScan() {
    val permissions = if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
      else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
      else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isNotEmpty()) {
      permissionLauncher.launch(missing.toTypedArray())
    } else {
      scope.launch {
        scanning = true
        status = "Skanuję pliki i porównuję ich zawartość..."
        try {
          val result = withContext(Dispatchers.IO) { scanPhotos(context) }
          scanned = result.first
          groups = result.second
          selected = emptySet()
          status = "Skanowanie zakończone. Wykryto ${result.first} zdjęć i ${result.second.size} grup identycznych kopii."
        } catch (e: Exception) {
          status = "Błąd skanowania: ${e.localizedMessage ?: "nieznany błąd"}"
        } finally { scanning = false }
      }
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
    if (grants.values.any { it }) requestScan()
    else status = "Brak zgody na dostęp do zdjęć. Nie zeskanowano biblioteki."
  }

  fun trashSelected() {
    if (Build.VERSION.SDK_INT < 30) {
      status = "Przenoszenie do kosza wymaga Androida 11 lub nowszego."
      return
    }
    val uris = groups.flatMap { it.items.drop(1) }.filter { it.uri.toString() in selected }.map { it.uri }
    if (uris.isEmpty()) {
      status = "Zaznacz kopie, które chcesz przenieść do kosza."
      return
    }
    try {
      val request = MediaStore.createTrashRequest(context.contentResolver, uris, true)
      trashLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    } catch (e: Exception) {
      status = "Nie udało się przygotować potwierdzenia Androida: ${e.localizedMessage}"
    }
  }

  Scaffold { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(18.dp)) {
      Text("Most Nowy", style = MaterialTheme.typography.headlineMedium)
      Text("Photos Cleaner", style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(8.dp))
      Text("Wykrywa identyczne pliki zdjęć na telefonie na podstawie sumy SHA-256. Skanowanie odbywa się lokalnie.")
      Spacer(Modifier.height(12.dp))
      Button(onClick = { requestScan() }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
        Text(if (scanning) "Skanowanie..." else "Skanuj zdjęcia")
      }
      Spacer(Modifier.height(8.dp))
      Text(status, style = MaterialTheme.typography.bodyMedium)
      if (scanned > 0) Text("Przeskanowane zdjęcia: $scanned")
      Spacer(Modifier.height(8.dp))
      if (groups.isNotEmpty()) {
        Text("Znalezione grupy: ${groups.size}", style = MaterialTheme.typography.titleMedium)
        Text("W każdej grupie pierwsze zdjęcie jest zachowywane. Zaznacz tylko dodatkowe kopie.")
        LazyColumn(Modifier.weight(1f)) {
          items(groups) { group ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
              Column(Modifier.padding(12.dp)) {
                Text("Identyczne kopie: ${group.items.size}", style = MaterialTheme.typography.titleSmall)
                group.items.forEachIndexed { index, item ->
                  Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (index == 0) {
                      Text("ZACHOWAJ", modifier = Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium)
                    } else {
                      Checkbox(
                        checked = item.uri.toString() in selected,
                        onCheckedChange = { checked ->
                          selected = if (checked) selected + item.uri.toString() else selected - item.uri.toString()
                        }
                      )
                    }
                    Column(Modifier.weight(1f)) {
                      Text(item.name, style = MaterialTheme.typography.bodyMedium)
                      Text("${item.size / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    }
                  }
                }
              }
            }
          }
        }
        Button(onClick = { trashSelected() }, modifier = Modifier.fillMaxWidth(), enabled = selected.isNotEmpty()) {
          Text("Przenieś zaznaczone do kosza (${selected.size})")
        }
      } else {
        Spacer(Modifier.height(12.dp))
        Text("Po skanowaniu pojawią się tu grupy identycznych zdjęć.")
      }
      Spacer(Modifier.height(10.dp))
      Text("Ograniczenie: aplikacja nie skanuje zdjęć dostępnych wyłącznie w chmurze Google Photos. Google udostępnia je aplikacjom przez wybór użytkownika, a nie pełny dostęp do całej biblioteki.", style = MaterialTheme.typography.bodySmall)
    }
  }
}

private fun scanPhotos(context: android.content.Context): Pair<Int, List<DuplicateGroup>> {
  val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
  val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_ADDED)
  val grouped = linkedMapOf<String, MutableList<PhotoItem>>()
  var count = 0
  context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
    while (cursor.moveToNext()) {
      val id = cursor.getLong(idCol)
      val uri = ContentUris.withAppendedId(collection, id)
      val name = cursor.getString(nameCol) ?: "photo_$id"
      val size = cursor.getLong(sizeCol)
      val date = cursor.getLong(dateCol)
      val digest = context.contentResolver.openInputStream(uri)?.use { input ->
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
          val n = input.read(buffer)
          if (n <= 0) break
          md.update(buffer, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
      } ?: continue
      count++
      grouped.getOrPut(digest) { mutableListOf() }.add(PhotoItem(uri, name, size, date, digest))
    }
  }
  val duplicates = grouped.filterValues { it.size > 1 }.map { DuplicateGroup(it.key, it.value) }
  return count to duplicates
}
