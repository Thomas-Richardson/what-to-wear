package com.richie.whattowear.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.richie.whattowear.MainViewModel
import com.richie.whattowear.data.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Filling in the clothes with AI: take or pick a photo, or describe the outfit in your own words.
 * The AI ticks what it finds below, and offers to add anything that isn't on the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiFill(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var words by rememberSaveable { mutableStateOf("") }
    var cameraMissing by remember { mutableStateOf(false) }
    // The camera app saves the photo in the app's private cache. It's only set up when you tap
    // "Take photo", it survives the app being paused while the camera is open, and it's deleted once read.
    val photoFile = remember { File(context.cacheDir, "photos/outfit.jpg") }
    var waitingFor by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun send(uri: Uri) {
        scope.launch {
            val jpeg = runCatching { withContext(Dispatchers.IO) { Photos.forAi(context, uri) } }.getOrNull()
            photoFile.delete()
            if (jpeg != null) vm.fillFromPhoto(jpeg) else vm.message = "Couldn't open that photo."
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = waitingFor
        waitingFor = null
        if (saved && uri != null) send(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::send) }
    val describe = { if (words.isNotBlank()) vm.fillFromDescription(words) }

    Section("Fill in with AI") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = {
                try {
                    photoFile.parentFile?.mkdirs()
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", photoFile)
                    waitingFor = uri
                    takePhoto.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    cameraMissing = true
                }
            }, enabled = vm.aiBusy == null) { Text("📷 Take photo") }
            OutlinedButton(
                shape = MaterialTheme.shapes.small,
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = vm.aiBusy == null,
            ) { Text("🖼️ Choose photo") }
        }
        if (cameraMissing) Text("No camera app found. Choose a photo instead.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = words,
            onValueChange = { words = it },
            label = { Text("Or describe what you're wearing") },
            placeholder = { Text("tweed jacket, chinos, my pea coat") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { describe() }),
            trailingIcon = { TextButton(onClick = describe, enabled = words.isNotBlank() && vm.aiBusy == null) { Text("Fill in") } },
            modifier = Modifier.fillMaxWidth(),
        )
        vm.aiBusy?.let { status ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        vm.aiProblem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        vm.aiResult?.let { result ->
            Text(
                if (result.itemIds.isEmpty()) "The AI didn't find anything it knows. Tick the clothes below."
                else "Ticked ${result.itemIds.size} below. Check them and change anything that's wrong.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.unsure.isNotBlank()) {
                Text("Not sure: ${result.unsure}", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
            }
            if (result.suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.suggestions.forEach { s ->
                        AssistChip(onClick = { vm.addSuggested(s) }, label = { Text("＋ Add ${s.name}") })
                    }
                }
            }
        }
    }
}
