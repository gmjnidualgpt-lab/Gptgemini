package com.mostnowy.photoscleaner

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.Uri as AndroidUri
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
data class CloudItem(val id: String, val label: String)

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
  var cloudSession by remember { mutableStateOf("") }
  var cloudStatus by remember { mutableStateOf("Połącz konto Google i wklej kod sesji.") }
  var cloudItems by remember { mutableStateOf<List<CloudItem>>(emptyList()) }
  var selectedCloudIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var cloudBusy by remember { mutableStateOf(false) }
  var cloudTab by remember { mutableIntStateOf(0) }

  val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
    status = if (result.resultCode == android.app.Activity.RESULT_OK) "Android potwierdził przeniesienie wybranych zdjęć do kosza." else "Operacja została anulowana."
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      selected = emptySet()
      groups = emptyList()
      status += " Uruchom skanowanie ponownie, aby odświeżyć wyniki."
    }
  }

  fun startScan() {
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

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
    if (grants.values.any { it }) startScan()
    else status = "Brak zgody na dostęp do zdjęć. Nie zeskanowano biblioteki."
  }

  fun requestScan() {
    val permissions = if (Build.VERSION.SDK_INT >= 34) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
      else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
      else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else startScan()
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
        OutlinedButton(
          onClick = {
            val allExtraCopies = groups.flatMap { it.items.drop(1) }.map { it.uri.toString() }.toSet()
            selected = if (selected.containsAll(allExtraCopies)) emptySet() else allExtraCopies
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(if (groups.flatMap { it.items.drop(1) }.isNotEmpty() && selected.size == groups.flatMap { it.items.drop(1) }.size) "Odznacz wszystkie" else "Zaznacz wszystkie kopie")
        }
        LazyColumn(Modifier.weight(1f)) {
          items(groups) { group ->
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
              Column(Modifier.padding(12.dp)) {
                Text("Identyczne kopie: ${group.items.size}", style = MaterialTheme.typography.titleSmall)
                group.items.forEachIndexed { index, item ->
                  Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AsyncImage(
                      model = item.uri,
                      contentDescription = item.name,
                      modifier = Modifier.size(64.dp).padding(end = 8.dp),
                      contentScale = ContentScale.Crop
                    )
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
      Divider(Modifier.padding(vertical = 12.dp))
      Text("Konta Google", style = MaterialTheme.typography.titleLarge)
      Text("Połącz konto Google. Po zgodzie Google wyświetli kod sesji. Wklej go poniżej.")
      Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, AndroidUri.parse(WORKER_URL + "/auth/google"))) }, modifier = Modifier.fillMaxWidth()) { Text("Połącz konto Google") }
      OutlinedTextField(value = cloudSession, onValueChange = { cloudSession = it.trim() }, label = { Text("Kod sesji Google") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
      TabRow(selectedTabIndex = cloudTab) { Tab(selected = cloudTab == 0, onClick = { cloudTab = 0 }, text = { Text("Drive") }); Tab(selected = cloudTab == 1, onClick = { cloudTab = 1 }, text = { Text("Gmail") }) }
      Button(onClick = {
        if (cloudSession.isBlank()) { cloudStatus = "Najpierw wklej kod sesji."; return@Button }
        cloudBusy = true
        scope.launch { try {
          val data = withContext(Dispatchers.IO) { fetchCloud(cloudSession, if (cloudTab == 0) "/api/drive/files" else "/api/gmail/messages") }
          cloudItems = if (cloudTab == 0) {
            val arr = data.optJSONArray("files") ?: JSONArray()
            (0 until arr.length()).map { i ->
              val f = arr.getJSONObject(i)
              CloudItem(f.optString("id"), f.optString("name", "Bez nazwy") + " | " + f.optString("mimeType", "plik") + " | " + f.optString("size", "?") + " B")
            }
          } else {
            val arr = data.optJSONArray("messages") ?: JSONArray()
            withContext(Dispatchers.IO) {
              (0 until arr.length()).map { i ->
                val id = arr.getJSONObject(i).optString("id")
                try {
                  val detail = fetchCloud(cloudSession, "/api/gmail/message?id=" + id)
                  val headers = detail.optJSONObject("payload")?.optJSONArray("headers")
                  fun header(name: String): String {
                    if (headers == null) return ""
                    for (j in 0 until headers.length()) {
                      val h = headers.getJSONObject(j)
                      if (h.optString("name").equals(name, true)) return h.optString("value")
                    }
                    return ""
                  }
                  CloudItem(id, header("From").ifBlank { "Nieznany nadawca" } + " | " + header("Subject").ifBlank { "(bez tematu)" } + " | " + header("Date"))
                } catch (e: Exception) { CloudItem(id, "Nie udało się pobrać szczegółów wiadomości") }
              }
            }
          }
          selectedCloudIds = emptySet()
          cloudStatus = "Pobrano " + cloudItems.size + " pozycji. Zaznacz pozycje do przeniesienia do kosza."
        } catch(e:Exception) { cloudStatus = "Błąd: "+(e.message ?: "nieznany") } finally { cloudBusy=false } }
      }, enabled = !cloudBusy, modifier = Modifier.fillMaxWidth()) { Text(if (cloudBusy) "Pobieranie..." else "Pobierz listę") }
      Text(cloudStatus, style = MaterialTheme.typography.bodySmall)
      if (cloudItems.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Zaznaczono: " + selectedCloudIds.size)
          TextButton(onClick = {
            selectedCloudIds = if (selectedCloudIds.size == cloudItems.size) emptySet() else cloudItems.map { it.id }.toSet()
          }) { Text(if (selectedCloudIds.size == cloudItems.size) "Odznacz wszystkie" else "Zaznacz wszystkie") }
        }
        cloudItems.forEach { item ->
          Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = item.id in selectedCloudIds, onCheckedChange = { checked ->
              selectedCloudIds = if (checked) selectedCloudIds + item.id else selectedCloudIds - item.id
            })
            Text(item.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
          }
        }
        Button(onClick = {
          if (selectedCloudIds.isEmpty()) { cloudStatus = "Najpierw zaznacz pozycje."; return@Button }
          cloudBusy = true
          scope.launch {
            try {
              val result = withContext(Dispatchers.IO) {
                trashCloud(cloudSession, if (cloudTab == 0) "/api/drive/trash" else "/api/gmail/trash", selectedCloudIds.toList())
              }
              val arr = result.optJSONArray("results") ?: JSONArray()
              var ok = 0
              for (i in 0 until arr.length()) if (arr.getJSONObject(i).optBoolean("ok")) ok++
              cloudStatus = "Przeniesiono do kosza: " + ok + " z " + arr.length() + "."
              selectedCloudIds = emptySet()
            } catch (e: Exception) { cloudStatus = "Błąd przenoszenia: " + (e.message ?: "nieznany") }
            finally { cloudBusy = false }
          }
        }, enabled = !cloudBusy && selectedCloudIds.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
          Text(if (cloudBusy) "Przenoszenie..." else "Przenieś zaznaczone do kosza")
        }
      }
      Text("Pozycje są przenoszone do kosza Google dopiero po Twoim wyborze.", style = MaterialTheme.typography.bodySmall)
    }
  }
}

private const val WORKER_URL = "https://google-drive-cleaner-ai.arobert9944.workers.dev"

private fun trashCloud(session: String, path: String, ids: List<String>): JSONObject {
  val c = URL(WORKER_URL + path).openConnection() as HttpURLConnection
  c.requestMethod = "POST"
  c.connectTimeout = 15000
  c.readTimeout = 60000
  c.doOutput = true
  c.setRequestProperty("Authorization", "Bearer " + session)
  c.setRequestProperty("Content-Type", "application/json")
  c.outputStream.use { it.write(JSONObject().put("ids", JSONArray(ids)).toString().toByteArray(Charsets.UTF_8)) }
  val status = c.responseCode
  val body = (if (status in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
  if (status !in 200..299) throw IllegalStateException("HTTP " + status + ": " + body)
  return JSONObject(body)
}

private fun fetchCloud(session: String, path: String): JSONObject {
  val c = URL(WORKER_URL + path).openConnection() as HttpURLConnection
  c.requestMethod = "GET"; c.connectTimeout = 15000; c.readTimeout = 20000
  c.setRequestProperty("Authorization", "Bearer " + session)
  val status = c.responseCode
  val body = (if (status in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
  if (status !in 200..299) throw IllegalStateException("HTTP " + status + ": " + body)
  return JSONObject(body)
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
