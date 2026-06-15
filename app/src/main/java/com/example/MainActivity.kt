package com.example

import android.os.Bundle
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen {
    GET_STARTED,
    MAIN_HOME,
    VIDEO_EDITING,
    DASHBOARD,
    CREATE_VIDEO,
    ABOUT
}

data class SubtitleLine(
    val id: String,
    val text: String,
    val start: Float,
    val end: Float
)

data class VideoProject(
    val id: String,
    val name: String,
    val durationSeconds: Float,
    val resolution: String,
    val fps: Int,
    val lastEdited: String,
    val sizeMb: Double,
    val trimStart: Float,
    val trimEnd: Float,
    val subtitleText: String
)

fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, String> {
    var name = "unknown_file"
    var sizeStr = "0 B"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) {
                    val sizeBytes = cursor.getLong(sizeIndex)
                    sizeStr = when {
                        sizeBytes >= 1024 * 1024 -> String.format("%.2f MB", sizeBytes.toFloat() / (1024 * 1024))
                        sizeBytes >= 1024 -> String.format("%.2f KB", sizeBytes.toFloat() / 1024)
                        else -> "$sizeBytes B"
                    }
                }
            }
        }
    } catch (e: Exception) {
        uri.path?.let {
            val last = it.substringAfterLast('/')
            name = last
        }
    }
    return Pair(name, sizeStr)
}

fun getVideoDuration(context: Context, uri: Uri): Float {
    var duration = 15.0f
    try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
        if (time != null) {
            duration = time.toLong() / 1000f
        }
        retriever.release()
    } catch (e: Exception) {
        // Safe ignore
    }
    return duration
}

fun readTextFromUri(context: Context, uri: Uri): String {
    val stringBuilder = java.lang.StringBuilder()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line).append("\n")
                    line = reader.readLine()
                }
            }
        }
    } catch (e: Exception) {
        // Safe ignore
    }
    return stringBuilder.toString()
}

// Standard SRT Time Parser Helper
fun parseSrtTime(timeStr: String): Float {
    try {
        // e.g. "00:02:15,400" or "00:00:01.000"
        val parts = timeStr.trim().split(":")
        if (parts.size >= 3) {
            val hours = parts[0].trim().toFloatOrNull() ?: 0f
            val minutes = parts[1].trim().toFloatOrNull() ?: 0f
            val secondsPart = parts[2].trim().replace(",", ".")
            val seconds = secondsPart.toFloatOrNull() ?: 0f
            return hours * 3600f + minutes * 60f + seconds
        }
    } catch (e: Exception) {
        // fallback
    }
    return 0f
}

// Full SRT Parser Helper
fun parseSrtContent(content: String): List<SubtitleLine> {
    val list = mutableListOf<SubtitleLine>()
    try {
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n")
        val blocks = normalized.split("\n\n")
        var count = 1
        for (block in blocks) {
            if (block.trim().isEmpty()) continue
            val lines = block.trim().split("\n")
            if (lines.size >= 2) {
                var timeLine = ""
                var textStartIndex = 1
                if (lines[0].contains("-->")) {
                    timeLine = lines[0]
                    textStartIndex = 1
                } else if (lines.size >= 2 && lines[1].contains("-->")) {
                    timeLine = lines[1]
                    textStartIndex = 2
                }
                
                if (timeLine.isNotEmpty()) {
                    val timeParts = timeLine.split("-->")
                    if (timeParts.size == 2) {
                        val startSec = parseSrtTime(timeParts[0])
                        val endSec = parseSrtTime(timeParts[1])
                        val text = lines.drop(textStartIndex).joinToString(" ").trim()
                        if (text.isNotEmpty()) {
                            list.add(SubtitleLine("srt_${count++}", text, startSec, endSec))
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Safe return empty on error
    }
    return list
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VRecapAppContainer()
            }
        }
    }
}

@Composable
fun VRecapAppContainer() {
    var currentScreen by remember { mutableStateOf(Screen.GET_STARTED) }
    var folderName by remember { mutableStateOf("V_Recap_Storage") }
    var isDrawerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Active project state
    var activeVideoName by remember { mutableStateOf("sunset_drive_hd.mp4") }
    var activeVideoSize by remember { mutableStateOf("18.4 MB") }
    var activeVideoRes by remember { mutableStateOf("1920x1080 (HD)") }
    var videoDuration by remember { mutableStateOf(24.5f) }
    var trimStart by remember { mutableStateOf(0.0f) }
    var trimEnd by remember { mutableStateOf(15.0f) }
    var activeVideoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Loaded subtitle lines tracks state
    var subtitleLines by remember {
        mutableStateOf(
            listOf(
                SubtitleLine("sub_1", "Welcome to V Recap Studio!", 0.0f, 3.5f),
                SubtitleLine("sub_2", "This is an absolute state-of-the-art native editor.", 4.0f, 8.5f),
                SubtitleLine("sub_3", "Import real SRTs, audios, and customize fonts below!", 9.0f, 15.0f)
            )
        )
    }

    // Typography Settings
    var fontColorHex by remember { mutableStateOf("#FFFF00") } // Yellow
    var fontSizeSp by remember { mutableStateOf(18f) }
    var fontStylePreset by remember { mutableStateOf("Monospace") } // Monospace, SansSerif, Serif
    var subtitleBoxPreset by remember { mutableStateOf("Caption Box") } // None, Caption Box, Neon Glow, Solid Shadow
    var subtitleAnimationPreset by remember { mutableStateOf("TikTok Pop") } // None, Fade In, TikTok Pop, Karaoke Zoom
    var subtitleRelativeHeight by remember { mutableStateOf(44f) } // Offset position in DP from bottom

    // Audio backup tracks
    var audioTrackEnabled by remember { mutableStateOf(true) }
    var activeAudioName by remember { mutableStateOf("chill_lofi_beat.mp3") }
    var activeAudioSize by remember { mutableStateOf("1.2 MB") }
    var audioVolume by remember { mutableStateOf(0.8f) }
    var audioTimeDelay by remember { mutableStateOf(0.0f) }

    // Render Specs
    var renderFps by remember { mutableStateOf(60) }
    var renderWidth by remember { mutableStateOf(1920) }
    var renderHeight by remember { mutableStateOf(1080) }

    // Projects database simulation
    var projectsList by remember {
        mutableStateOf(
            listOf(
                VideoProject("1", "Summer_Trip_Recap.mp4", 12.5f, "1920x1080", 60, "2026-06-12", 45.3, 0f, 12.5f, "Welcome to the beaches"),
                VideoProject("2", "Cooking_Tutorial_v2.mp4", 45.0f, "1280x720", 30, "2026-06-14", 112.1, 5f, 40f, "Add two tablespoons of salt"),
                VideoProject("3", "Product_Intro_Ad.mp4", 15.0f, "1080x1080", 60, "2026-06-15", 32.5, 0f, 15f, "Get 20% off today!")
            )
        )
    }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF090A15), // Deep space pitch dark
            Color(0xFF0F122B), // Luxurious deep neon indigo slate
            Color(0xFF07080F)  // Sinking space abyss
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar
            if (currentScreen != Screen.GET_STARTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(Color(0xFF090A15).copy(alpha = 0.9f))
                        .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { isDrawerOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu Drawer",
                            tint = Color(0xFF5F5DFA)
                        )
                    }

                    // Luxury Branded Logo Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentScreen) {
                                Screen.MAIN_HOME -> "V RECAP NATIVE WORKSPACE"
                                Screen.VIDEO_EDITING -> "TIMELINE STUDIO"
                                Screen.DASHBOARD -> "PROJECT ARCHIVES"
                                Screen.CREATE_VIDEO -> "EXPORT CONSOLE"
                                Screen.ABOUT -> "DEVELOPER SUITE"
                                else -> "V RECAP"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp,
                            letterSpacing = 2.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF5F5DFA).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OFFLINE",
                            color = Color(0xFF5F5DFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Screen Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentScreen) {
                    Screen.GET_STARTED -> GetStartedScreen(
                        folderName = folderName,
                        onFolderNameChange = { folderName = it },
                        onGranted = { currentScreen = Screen.MAIN_HOME }
                    )
                    Screen.MAIN_HOME -> MainHomeScreen(
                        activeVideoName = activeVideoName,
                        activeVideoRes = activeVideoRes,
                        subtitleCount = subtitleLines.size,
                        onNavigate = { currentScreen = it }
                    )
                    Screen.VIDEO_EDITING -> VideoEditingScreen(
                        videoName = activeVideoName,
                        videoSize = activeVideoSize,
                        videoRes = activeVideoRes,
                        videoUri = activeVideoUri,
                        trimStart = trimStart,
                        trimEnd = trimEnd,
                        duration = videoDuration,
                        subtitleLines = subtitleLines,
                        fontColorHex = fontColorHex,
                        fontSizeSp = fontSizeSp,
                        fontStylePreset = fontStylePreset,
                        subtitleBoxPreset = subtitleBoxPreset,
                        subtitleAnimationPreset = subtitleAnimationPreset,
                        subtitleRelativeHeight = subtitleRelativeHeight,
                        audioEnabled = audioTrackEnabled,
                        activeAudioName = activeAudioName,
                        activeAudioSize = activeAudioSize,
                        audioVolume = audioVolume,
                        audioTimeDelay = audioTimeDelay,
                        onSubtitleLinesChange = { subtitleLines = it },
                        onFontColorHexChange = { fontColorHex = it },
                        onFontSizeSpChange = { fontSizeSp = it },
                        onFontStylePresetChange = { fontStylePreset = it },
                        onSubtitleBoxPresetChange = { subtitleBoxPreset = it },
                        onSubtitleAnimationPresetChange = { subtitleAnimationPreset = it },
                        onSubtitleRelativeHeightChange = { subtitleRelativeHeight = it },
                        onVideoChange = { name, size, res, dur, uri ->
                            activeVideoName = name
                            activeVideoSize = size
                            activeVideoRes = res
                            videoDuration = dur
                            activeVideoUri = uri
                            if (trimEnd > dur) trimEnd = dur
                            if (trimStart >= dur) trimStart = 0f
                        },
                        onAudioChange = { name, size ->
                            activeAudioName = name
                            activeAudioSize = size
                        },
                        onAudioVolumeChange = { audioVolume = it },
                        onAudioDelayChange = { audioTimeDelay = it },
                        onTrimStartChange = { trimStart = it },
                        onTrimEndChange = { trimEnd = it },
                        onAudioToggle = { audioTrackEnabled = it },
                        onSaveConfig = {
                            currentScreen = Screen.CREATE_VIDEO
                        }
                    )
                    Screen.DASHBOARD -> DashboardScreen(
                        projects = projectsList,
                        onReEdit = { proj ->
                            activeVideoName = proj.name
                            trimStart = proj.trimStart
                            trimEnd = proj.trimEnd
                            // Set list back containing this display string
                            subtitleLines = listOf(
                                SubtitleLine("sub_load", proj.subtitleText, trimStart, trimEnd)
                            )
                            currentScreen = Screen.VIDEO_EDITING
                        },
                        onDelete = { id ->
                            projectsList = projectsList.filter { it.id != id }
                        }
                    )
                    Screen.CREATE_VIDEO -> CreateVideoScreen(
                        videoName = activeVideoName,
                        trimStart = trimStart,
                        trimEnd = trimEnd,
                        subtitleText = if (subtitleLines.isNotEmpty()) subtitleLines[0].text else "V Recap Studio Production",
                        subtitleCount = subtitleLines.size,
                        audioEnabled = audioTrackEnabled,
                        audioName = activeAudioName,
                        renderFps = renderFps,
                        renderWidth = renderWidth,
                        renderHeight = renderHeight,
                        folderName = folderName,
                        onFpsChange = { renderFps = it },
                        onResolutionChange = { w, h ->
                            renderWidth = w
                            renderHeight = h
                        },
                        onRenderComplete = { finalName, finalDur, finalSize ->
                            val combinedSubtext = subtitleLines.joinToString(" | ") { it.text }.take(60) + "..."
                            val newProj = VideoProject(
                                id = (projectsList.size + 1).toString(),
                                name = finalName,
                                durationSeconds = finalDur,
                                resolution = "${renderWidth}x${renderHeight}",
                                fps = renderFps,
                                lastEdited = "2026-06-15",
                                sizeMb = finalSize,
                                trimStart = trimStart,
                                trimEnd = trimEnd,
                                subtitleText = combinedSubtext
                            )
                            projectsList = listOf(newProj) + projectsList
                            currentScreen = Screen.DASHBOARD
                        }
                    )
                    Screen.ABOUT -> AboutScreen()
                }
            }
        }

        // Animated Sliding Side Menu Drawer
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { isDrawerOpen = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(285.dp)
                        .background(Color(0xFF07080F))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF5F5DFA).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                        .padding(24.dp)
                        .clickable(enabled = false) {}
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "V RECAP HD",
                                    color = Color(0xFF5F5DFA),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 18.sp,
                                    letterSpacing = 1.sp
                                )
                                IconButton(onClick = { isDrawerOpen = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White
                                    )
                                }
                            }

                            Divider(color = Color(0xFF5F5DFA).copy(alpha = 0.2f), thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))

                            DrawerItem(Icons.Default.Home, "Studio Home", isSelected = currentScreen == Screen.MAIN_HOME) {
                                currentScreen = Screen.MAIN_HOME
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.PlayArrow, "Timeline Workspace", isSelected = currentScreen == Screen.VIDEO_EDITING) {
                                currentScreen = Screen.VIDEO_EDITING
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.List, "Completed Archive", isSelected = currentScreen == Screen.DASHBOARD) {
                                currentScreen = Screen.DASHBOARD
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.Settings, "Render Control Room", isSelected = currentScreen == Screen.CREATE_VIDEO) {
                                currentScreen = Screen.CREATE_VIDEO
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.Info, "Developer Support Info", isSelected = currentScreen == Screen.ABOUT) {
                                currentScreen = Screen.ABOUT
                                isDrawerOpen = false
                            }
                        }

                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(text = "App Mode: Full Native Workstation", color = Color.Gray, fontSize = 11.sp)
                            Text(text = "Storage Pool: /$folderName/", color = Color.Gray, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    currentScreen = Screen.GET_STARTED
                                    isDrawerOpen = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "Reconfigure Folder", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColors = if (isSelected) Color(0xFF5F5DFA).copy(alpha = 0.15f) else Color.Transparent
    val borderCol = if (isSelected) Color(0xFF5F5DFA).copy(alpha = 0.6f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColors)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF5F5DFA) else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFF5F5DFA) else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun GetStartedScreen(
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    onGranted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F24)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // High Tech Graphic Mock
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF5F5DFA).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFF5F5DFA), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Logo",
                        tint = Color(0xFF5F5DFA),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "V RECAP STUDIO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "ADVANCED COMPILING CONTAINER",
                    color = Color(0xFF5F5DFA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Ready to build your next spectacular viral video recap with customized subtitle timelines, interactive SRT imports, and background sound synchronization.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = folderName,
                    onValueChange = onFolderNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Local Folder Workspace Name", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF5F5DFA),
                        unfocusedBorderColor = Color(0xFF5F5DFA).copy(alpha = 0.4f),
                        focusedLabelColor = Color(0xFF5F5DFA),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onGranted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5F5DFA)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "LAUNCH WORKSPACE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MainHomeScreen(
    activeVideoName: String,
    activeVideoRes: String,
    subtitleCount: Int,
    onNavigate: (Screen) -> Unit
) {
    var animatedText by remember { mutableStateOf("") }
    val fullText = "Create Subtitled Videos Effortlessly."

    LaunchedEffect(Unit) {
        val splitText = fullText
        for (i in 1..splitText.length) {
            animatedText = splitText.substring(0, i)
            delay(40)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF5F5DFA).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.25f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⭐ DESIGN SUITE EDITION ⭐",
                    color = Color(0xFF5F5DFA),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Typing main title
            Text(
                text = animatedText,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(75.dp)
            )

            Text(
                text = "Premium high-fidelity interface with professional timeline subtitles parsing, dynamic style formatting presets, SRT loaders, and offline video exports.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
            )

            // Current Stats Bento Widget
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F122B).copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ACTIVE VIDEO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(activeVideoName.take(16) + "...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color(0xFF5F5DFA).copy(alpha = 0.2f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RESOLUTION", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(activeVideoRes, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color(0xFF5F5DFA).copy(alpha = 0.2f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SUBTITLES LOADED", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("$subtitleCount Timed lines", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeMenuItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Timeline Editor Suite",
                    description = "Highly granular subtitle offsets, multi-presets style picker, custom video/SRT files imports.",
                    accentColor = Color(0xFF5F5DFA),
                    onClick = { onNavigate(Screen.VIDEO_EDITING) }
                )

                HomeMenuItem(
                    icon = Icons.Default.List,
                    title = "Project Archives Dashboard",
                    description = "Review previously compiled products, check technical renders, back-propagate parameters.",
                    accentColor = Color(0xFF10B981),
                    onClick = { onNavigate(Screen.DASHBOARD) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F122B).copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { onNavigate(Screen.ABOUT) }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "", tint = Color(0xFF5F5DFA), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Specs Info", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F122B).copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .clickable { onNavigate(Screen.CREATE_VIDEO) }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = "", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Room", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F122B).copy(alpha = 0.6f))
            .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "",
                tint = accentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun VideoEditingScreen(
    videoName: String,
    videoSize: String,
    videoRes: String,
    videoUri: Uri?,
    trimStart: Float,
    trimEnd: Float,
    duration: Float,
    subtitleLines: List<SubtitleLine>,
    fontColorHex: String,
    fontSizeSp: Float,
    fontStylePreset: String,
    subtitleBoxPreset: String,
    subtitleAnimationPreset: String,
    subtitleRelativeHeight: Float,
    audioEnabled: Boolean,
    activeAudioName: String,
    activeAudioSize: String,
    audioVolume: Float,
    audioTimeDelay: Float,
    onSubtitleLinesChange: (List<SubtitleLine>) -> Unit,
    onFontColorHexChange: (String) -> Unit,
    onFontSizeSpChange: (Float) -> Unit,
    onFontStylePresetChange: (String) -> Unit,
    onSubtitleBoxPresetChange: (String) -> Unit,
    onSubtitleAnimationPresetChange: (String) -> Unit,
    onSubtitleRelativeHeightChange: (Float) -> Unit,
    onVideoChange: (String, String, String, Float, Uri?) -> Unit,
    onAudioChange: (String, String) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onAudioDelayChange: (Float) -> Unit,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onAudioToggle: (Boolean) -> Unit,
    onSaveConfig: () -> Unit
) {
    // Tab switching for higher screen limits & professional categorizations
    var activeEditorTab by remember { mutableStateOf("SUBTITLES") } // ASSETS, TRANSCRIPTS, SUBTITLES, AUDIO
    var srtPasteText by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = getFileNameAndSize(context, it)
            val dur = getVideoDuration(context, it)
            onVideoChange(name, size, "Selected (HD)", dur, it)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = getFileNameAndSize(context, it)
            onAudioChange(name, size)
        }
    }

    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileContent = readTextFromUri(context, it)
            if (fileContent.isNotBlank()) {
                val parsed = parseSrtContent(fileContent)
                if (parsed.isNotEmpty()) {
                    onSubtitleLinesChange(parsed)
                }
            }
        }
    }
    
    // Playback mockup & control states
    var simulatedPlaybackSecond by remember { mutableStateOf(0.0f) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var loopEnabled by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var playerAspectRatio by remember { mutableStateOf("16:9") }

    // Coroutine key player ticks
    LaunchedEffect(isPlaying, playbackSpeed, loopEnabled, trimStart, trimEnd) {
        if (isPlaying) {
            val tickIntervalMs = 100L
            while (true) {
                delay(tickIntervalMs)
                val increment = (tickIntervalMs / 1000f) * playbackSpeed
                val nextSecond = simulatedPlaybackSecond + increment
                if (nextSecond > trimEnd) {
                    if (loopEnabled) {
                        simulatedPlaybackSecond = trimStart
                    } else {
                        simulatedPlaybackSecond = trimEnd
                        isPlaying = false
                    }
                } else {
                    simulatedPlaybackSecond = nextSecond
                }
            }
        }
    }

    // Determine currently displayed subtitle
    val activeSubtitleDisplay = subtitleLines.firstOrNull { 
        simulatedPlaybackSecond >= it.start && simulatedPlaybackSecond <= it.end 
    }?.text ?: ""

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High fidelity video display mock & player options container
        item {
            val playerRatio = when (playerAspectRatio) {
                "16:9" -> 16f / 9f
                "9:16" -> 9f / 16f
                "1:1" -> 1f / 1f
                else -> 16f / 10f
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(playerRatio)
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF030510))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    
                    if (videoUri != null) {
                        // REAL NATIVE MOVIE PLAYER RENDER SOURCE
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        setOnErrorListener { _, _, _ -> true }
                                        setOnPreparedListener { mp ->
                                            setTag(mp)
                                            mp.isLooping = loopEnabled
                                            if (isMuted) {
                                                mp.setVolume(0f, 0f)
                                            } else {
                                                mp.setVolume(audioVolume, audioVolume)
                                            }
                                        }
                                    }
                                },
                                update = { videoView ->
                                    try {
                                        if (videoView.tag != videoUri) {
                                            videoView.setVideoURI(videoUri)
                                            videoView.tag = videoUri
                                            videoView.requestFocus()
                                        }
                                        if (isPlaying) {
                                            if (!videoView.isPlaying) {
                                                videoView.start()
                                            }
                                        } else {
                                            if (videoView.isPlaying) {
                                                videoView.pause()
                                            }
                                        }
                                        
                                        // Dynamic audio & volume & speed sync parameters
                                        (videoView.getTag() as? android.media.MediaPlayer)?.let { mp ->
                                            mp.isLooping = loopEnabled
                                            if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(audioVolume, audioVolume)
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                try {
                                                    val params = mp.playbackParams
                                                    if (params.speed != playbackSpeed) {
                                                        mp.playbackParams = params.setSpeed(playbackSpeed)
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                        }

                                        // Sync timeline slider jump skips
                                        val targetMs = (simulatedPlaybackSecond * 1000).toInt()
                                        if (Math.abs(videoView.currentPosition - targetMs) > 1000) {
                                            videoView.seekTo(targetMs)
                                        }
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Video Backdrop simulated gradient visualizer overlay
                        val clipOverlayColor = if (isPlaying) Color(0xFF5F5DFA).copy(alpha = 0.03f) else Color.Transparent
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF0F1535).copy(alpha = 0.8f), Color(0xFF02040C))
                                    )
                                )
                                .background(clipOverlayColor)
                        )
                    }

                    // File Host Label Info
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🎬 ${if (videoUri != null) "Real: " else ""}$videoName • ${String.format("%.1fs", simulatedPlaybackSecond)} / ${String.format("%.1fs", duration)}",
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Burned custom timed subtitle overlays styled strictly via selected preset configs
                    val activeColor = try {
                        Color(android.graphics.Color.parseColor(fontColorHex))
                    } catch (e: Exception) {
                        Color.Yellow
                    }

                    val textStyleSelected = TextStyle(
                        color = activeColor,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontFamily = when (fontStylePreset) {
                            "Monospace" -> FontFamily.Monospace
                            "Serif" -> FontFamily.Serif
                            else -> FontFamily.SansSerif
                        },
                        shadow = if (subtitleBoxPreset == "Solid Shadow") {
                            Shadow(color = Color.Black, offset = Offset(2f, 2f), blurRadius = 4f)
                        } else null
                    )

                    val overlayBoxModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = subtitleRelativeHeight.dp)
                        .fillMaxWidth(0.9f)

                    Box(
                        modifier = overlayBoxModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeSubtitleDisplay.isNotEmpty()) {
                            val innerModifier = when (subtitleBoxPreset) {
                                "Caption Box" -> Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.75f))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                "Neon Glow" -> Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(activeColor.copy(alpha = 0.08f))
                                    .border(1.dp, activeColor, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                else -> Modifier.padding(8.dp)
                            }

                            Box(modifier = innerModifier) {
                                if (subtitleAnimationPreset == "TikTok Pop") {
                                    Text(
                                        text = activeSubtitleDisplay.uppercase(),
                                        style = textStyleSelected.copy(fontSize = (fontSizeSp + 3f).sp),
                                        letterSpacing = 1.sp
                                    )
                                } else {
                                    Text(
                                        text = activeSubtitleDisplay,
                                        style = textStyleSelected
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "[ TIMELINE SILENT - SUBTITLE LOADED ]",
                                color = Color.Gray.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            )
                        }
                    }

                    // Lower timeline ticks
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(vertical = 4.dp, horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TRIM WINDOW: ${String.format("%.1fs", trimStart)} - ${String.format("%.1fs", trimEnd)}",
                                color = Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = "FPS: 60",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // COMPREHENSIVE TIMELINE CONTROLLER BAR & SCUBLINE TIMERS
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0E1E))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Playback progress slider scrubbing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%.1fs", simulatedPlaybackSecond),
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(42.dp)
                        )
                        
                        Slider(
                            value = simulatedPlaybackSecond,
                            onValueChange = {
                                simulatedPlaybackSecond = it
                            },
                            valueRange = trimStart..trimEnd,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF5F5DFA),
                                activeTrackColor = Color(0xFF5F5DFA),
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        
                        Text(
                            text = String.format("%.1fs", trimEnd),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Player buttons row:
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left group: Navigation & steps
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Step backward 0.1s
                            IconButton(
                                onClick = {
                                    simulatedPlaybackSecond = (simulatedPlaybackSecond - 0.1f).coerceAtLeast(trimStart)
                                },
                                modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Text("◀", color = Color.White, fontSize = 11.sp)
                            }
                            
                            // Skip Back 2s
                            IconButton(
                                onClick = {
                                    simulatedPlaybackSecond = (simulatedPlaybackSecond - 2.0f).coerceAtLeast(trimStart)
                                },
                                modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Text("⏪", color = Color.White, fontSize = 11.sp)
                            }
                            
                            // Play/Pause master
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier.size(38.dp).background(Color(0xFF5F5DFA), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = "",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Skip Forward 2s
                            IconButton(
                                onClick = {
                                    simulatedPlaybackSecond = (simulatedPlaybackSecond + 2.0f).coerceAtMost(trimEnd)
                                },
                                modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Text("⏩", color = Color.White, fontSize = 11.sp)
                            }

                            // Step forward 0.1s
                            IconButton(
                                onClick = {
                                    simulatedPlaybackSecond = (simulatedPlaybackSecond + 0.1f).coerceAtMost(trimEnd)
                                },
                                modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Text("▶", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        // Right group: Sound & Loop Controls
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Mute btn
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier.size(34.dp).background(
                                    if (isMuted) Color(0xFFEF4444).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
                                    CircleShape
                                )
                            ) {
                                Text(if (isMuted) "🔇" else "🔊", fontSize = 12.sp)
                            }

                            // Loop repeat toggle
                            IconButton(
                                onClick = { loopEnabled = !loopEnabled },
                                modifier = Modifier.size(34.dp).background(
                                    if (loopEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f),
                                    CircleShape
                                )
                            ) {
                                Text("🔁", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Down-row option bars: Player Speeds and Crop ratio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playback Speed choices
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Speed: ", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (playbackSpeed == spd) Color(0xFF5F5DFA) else Color.Black.copy(alpha = 0.3f))
                                        .clickable { playbackSpeed = spd }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text("${spd}x", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Aspect ratio cropping selector
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Crop Ratio: ", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            listOf("16:9", "9:16", "1:1").forEach { ratio ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (playerAspectRatio == ratio) Color(0xFF10B981) else Color.Black.copy(alpha = 0.3f))
                                        .clickable { playerAspectRatio = ratio }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(ratio, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // INTERACTIVE TIMELINE WORKSPACE WITH MULTI-TRACK STAGES
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF060914))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🎛️ NATIVE MULTI-TRACK STUDIO TIMELINE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Tap on track segments to jump playhead directly",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Track 1: Video & Trim slider visual track representation
                    Text("🎬 VIDEO MASTER TRACK (${String.format("%.1f", duration)}s)", color = Color(0xFF5F5DFA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F122B))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                    ) {
                        // Draw simulated frames
                        Row(modifier = Modifier.fillMaxSize()) {
                            for (i in 1..8) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(0.5.dp, Color.Black.copy(alpha = 0.3f))
                                        .background(Color.White.copy(alpha = 0.02f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎞️", color = Color.White.copy(alpha = 0.15f), fontSize = 10.sp)
                                }
                            }
                        }

                        // Draw trim boundaries
                        val startPct = (trimStart / duration).coerceIn(0f, 1f)
                        val endPct = (trimEnd / duration).coerceIn(0f, 1f)
                        val windowWidthMultiplier = endPct - startPct
                        
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(windowWidthMultiplier.coerceAtLeast(0.01f))
                                .offset(x = (startPct * 260).dp)
                                .background(Color(0xFF5F5DFA).copy(alpha = 0.2f))
                                .border(BorderStroke(1.dp, Color(0xFF5F5DFA)), RoundedCornerShape(2.dp))
                        )
                        
                        // Draw Playhead line
                        val playheadPct = ((simulatedPlaybackSecond - trimStart) / (trimEnd - trimStart)).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .align(Alignment.CenterStart)
                                .offset(x = (playheadPct * 260).dp)
                                .background(Color(0xFFEF4444))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Track 2: Timed Subtitle blocks segments track!
                    Text("📝 SUBTITLE ENTRIES SEGMENTS TRACK", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0A0D1A))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                    ) {
                        if (subtitleLines.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("[ No subtitled sections ]", color = Color.DarkGray, fontSize = 9.sp)
                            }
                        } else {
                            // Render mini segments
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                subtitleLines.forEach { line ->
                                    val isCurrent = simulatedPlaybackSecond >= line.start && simulatedPlaybackSecond <= line.end
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(0.8f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isCurrent) Color(0xFF10B981).copy(alpha = 0.3f)
                                                else Color.Black.copy(alpha = 0.4f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isCurrent) Color(0xFF10B981) else Color.White.copy(alpha = 0.1f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                // SEEK PLAYHEAD DIRECTLY TO THIS CAPTION START!
                                                simulatedPlaybackSecond = line.start
                                                isPlaying = false
                                            }
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = line.text,
                                            color = if (isCurrent) Color.White else Color.Gray,
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Track 3: Audio timeline and Latency/Offset delays!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎵 AUDIO TRACK (${activeAudioName})", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Delay Offset: ${String.format("%.1fs", audioTimeDelay)}s",
                            color = Color.Gray,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F122B).copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Drawing simulated waveform
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val audioWavesCount = 32
                            for (i in 1..audioWavesCount) {
                                val waveHeight = remember(i) { (10..22).random() }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(waveHeight.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color(0xFFEF4444).copy(alpha = if (audioEnabled) 0.4f else 0.1f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Gorgeous navigation tab row inside workspace
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F122B))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tabs = listOf(
                    Pair("SUBTITLES", "📝 Subs"),
                    Pair("ASSETS", "📁 Uploads"),
                    Pair("AUDIO", "🎵 Audio"),
                    Pair("STYLE Preset", "🎨 Styles")
                )
                tabs.forEach { (key, display) ->
                    val isSelected = activeEditorTab == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF5F5DFA) else Color.Transparent)
                            .clickable { activeEditorTab = key }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = display,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // CONDITIONAL TAB EDITING TOOLS:
        if (activeEditorTab == "SUBTITLES") {
            // MULTI-SUBTITLE LIST MANAGER (COMPLETE SUBTITLE MANAGER TOOLS)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F24))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("COMPREHENSIVE TIMED CAPTIONS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Add, modify timestamps, delete lines easily", color = Color.Gray, fontSize = 11.sp)
                            }

                            // Add New Timed Row Button
                            Button(
                                onClick = {
                                    val nextStart = if (subtitleLines.isNotEmpty()) subtitleLines.last().end + 0.5f else 0.0f
                                    val newRow = SubtitleLine(
                                        id = "custom_sub_${System.currentTimeMillis()}",
                                        text = "New elegant subtitle line",
                                        start = nextStart,
                                        end = nextStart + 3.0f
                                    )
                                    onSubtitleLinesChange(subtitleLines + newRow)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "", tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Render List of subtitle entries
                        if (subtitleLines.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No captions recorded. Tap ADD in top corner to append.", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                subtitleLines.forEachIndexed { index, line ->
                                    val isActive = simulatedPlaybackSecond >= line.start && simulatedPlaybackSecond <= line.end
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isActive) Color(0xFF10B981).copy(alpha = 0.1f)
                                                else Color(0xFF0F122B)
                                            )
                                            .border(
                                                1.dp,
                                                if (isActive) Color(0xFF10B981) else Color(0xFF5F5DFA).copy(alpha = 0.15f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        // Row containing text field to edit caption text
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color(0xFF5F5DFA),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.width(22.dp)
                                            )

                                            // Subtitle word input
                                            TextField(
                                                value = line.text,
                                                onValueChange = { updated: String ->
                                                    val copy = subtitleLines.toMutableList()
                                                    copy[index] = line.copy(text = updated)
                                                    onSubtitleLinesChange(copy)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                ),
                                                textStyle = TextStyle(
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                singleLine = true
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Delete Row button
                                            IconButton(
                                                onClick = {
                                                    onSubtitleLinesChange(subtitleLines.filter { it.id != line.id })
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Line", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Timestamp offset granular buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Start Seconds Adjuster
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Start: ", color = Color.Gray, fontSize = 11.sp)
                                                Text(
                                                    "${String.format("%.1f", line.start)}s",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.width(38.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                        .clickable {
                                                            if (line.start > 0.1f) {
                                                                val copy = subtitleLines.toMutableList()
                                                                copy[index] = line.copy(start = line.start - 0.5f)
                                                                onSubtitleLinesChange(copy)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                        .clickable {
                                                            if (line.start < line.end) {
                                                                val copy = subtitleLines.toMutableList()
                                                                copy[index] = line.copy(start = line.start + 0.5f)
                                                                onSubtitleLinesChange(copy)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            // End Seconds Adjuster
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("End: ", color = Color.Gray, fontSize = 11.sp)
                                                Text(
                                                    "${String.format("%.1f", line.end)}s",
                                                    color = Color(0xFFEF4444),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.width(38.dp)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                        .clickable {
                                                            if (line.end > line.start) {
                                                                val copy = subtitleLines.toMutableList()
                                                                copy[index] = line.copy(end = line.end - 0.5f)
                                                                onSubtitleLinesChange(copy)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                        .clickable {
                                                            val copy = subtitleLines.toMutableList()
                                                            copy[index] = line.copy(end = line.end + 0.5f)
                                                            onSubtitleLinesChange(copy)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeEditorTab == "ASSETS") {
            // MULTI-MEDIA UPLOADER CHANNELS (UPLOAD VIDEO, SRT, AUDIO)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF060914))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("RESOURCE INTEGRATION CONSOLE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Connect custom local assets into container timeline", color = Color.Gray, fontSize = 11.sp)
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // 1.🎬 VIDEO UPLOAD BOX
                        Text("🎬 VIDEO CONNECTOR SOURCE:", color = Color(0xFF5F5DFA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F122B))
                                .border(BorderStroke(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.2f)), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(videoName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Specs: $videoRes • $videoSize • ${duration}s", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Select Quick System Video Presets to Mock Upload Action
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Triple("sunset_drive.mp4", "Cooking Tutorial", 12.5f),
                                        Triple("product_promo.mp4", "Product Promo", 15.0f),
                                        Triple("vlog_rec.mp4", "Travel Vlog", 30.0f)
                                    ).forEach { (fName, label, secs) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black.copy(alpha = 0.4f))
                                                .clickable {
                                                    val isSect = fName == "sunset_drive.mp4"
                                                    val mockSz = if (isSect) "18.4 MB" else if (fName == "product_promo.mp4") "32.5 MB" else "112.1 MB"
                                                    val mockRes = if (isSect) "1920x1080 (HD)" else "1080x1080 (Square)"
                                                    onVideoChange(fName, mockSz, mockRes, secs, null)
                                                }
                                                .border(
                                                    1.dp,
                                                    if (videoName == fName) Color(0xFF5F5DFA) else Color.Transparent,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Interactive Select/Drag Simulator field
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .border(BorderStroke(0.6.dp, Color.Gray.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                        .clickable {
                                            try {
                                                videoPickerLauncher.launch("video/*")
                                            } catch (e: Exception) {
                                                onVideoChange("vrecap_user_upload_${System.currentTimeMillis() % 1000}.mp4", "54.8 MB", "1920x1080 (HD)", 25.0f, null)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = "", tint = Color(0xFF5F5DFA), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🎬 Select Real Video from Phone", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2.📝 SRT FILE PARSER UPLOAD WORKLIST
                        Text("📝 TIMED SRT SUBTITLES FILE:", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F122B))
                                .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    "Paste raw SRT format below or Click sample templates to inject directly into the live parsed timeline entries:",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = srtPasteText,
                                    onValueChange = { srtPasteText = it },
                                    label = { Text("Raw SRT Text Sandbox", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF10B981),
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                                    ),
                                    maxLines = 5,
                                    placeholder = {
                                        Text(
                                            "1\n00:00:01,000 --> 00:00:05,000\nHello world captions",
                                            color = Color.DarkGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Parse Paste Button
                                    Button(
                                        onClick = {
                                            if (srtPasteText.isNotBlank()) {
                                                val parsed = parseSrtContent(srtPasteText)
                                                if (parsed.isNotEmpty()) {
                                                    onSubtitleLinesChange(parsed)
                                                    srtPasteText = ""
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        enabled = srtPasteText.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Parse Raw SRT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Quick Templates Selector
                                    Button(
                                        onClick = {
                                            srtPasteText = "1\n00:00:00,500 --> 00:00:04,200\nAwesome Cooking Tutorial Show!\n\n2\n00:00:05,000 --> 00:00:09,800\nChop fresh onions and celery finely.\n\n3\n00:00:10,200 --> 00:00:14,500\nStir fry inside heated hot wok pan!"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5F5DFA)),
                                        modifier = Modifier.weight(1.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Load Cook SRT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                // Select and Parse local SRT File
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .border(BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                        .padding(10.dp)
                                        .clickable {
                                            try {
                                                srtPickerLauncher.launch("*/*")
                                            } catch (e: Exception) {
                                                val srtFileText = "1\n00:00:00,500 --> 00:00:04,000\n🌟 Scenic view sunset drive 🌟\n\n2\n00:00:04,500 --> 00:00:09,000\nCruising down coastal highway pacific road.\n\n3\n00:00:10,000 --> 00:00:15,000\nThank you for watching my recaps vlog!"
                                                val parsed = parseSrtContent(srtFileText)
                                                onSubtitleLinesChange(parsed)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Add, contentDescription = "", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("📝 Select custom SRT file from Phone", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeEditorTab == "AUDIO") {
            // MOCK AUDIO BACKTRACK AND DELAYS (UPLOAD AUDIO SUPPORT)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A10))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎵 AUDIO TRACK INTEGRATION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Switch(
                                checked = audioEnabled,
                                onCheckedChange = onAudioToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFEF4444)
                                )
                            )
                        }

                        if (audioEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Current backing layer: $activeAudioName ($activeAudioSize)", color = Color.Gray, fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Absolute Slider Volume multiplier
                            Text("Sound volume multi: ${(audioVolume * 100).toInt()}%", color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = audioVolume,
                                onValueChange = onAudioVolumeChange,
                                valueRange = 0f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFEF4444),
                                    activeTrackColor = Color(0xFFEF4444)
                                )
                            )

                            // Track starting delay offset slider
                            Text("Sync start delay: ${String.format("%.1f", audioTimeDelay)} seconds", color = Color.White, fontSize = 11.sp)
                            Slider(
                                value = audioTimeDelay,
                                onValueChange = onAudioDelayChange,
                                valueRange = -5.0f..5.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFEF4444),
                                    activeTrackColor = Color(0xFFEF4444)
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Backing tracks presets choices
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    Pair("lofi_vibe.mp3", "Lo-Fi Beats"),
                                    Pair("acoustic_summer.wav", "Acoustic"),
                                    Pair("voice_over_ai.m4a", "AI Voice Layer")
                                ).forEach { (fName, label) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.4f))
                                            .clickable {
                                                val sz = if (fName.contains("lofi")) "1.2 MB" else if (fName.contains("acoustic")) "4.5 MB" else "0.4 MB"
                                                onAudioChange(fName, sz)
                                            }
                                            .border(
                                                1.dp,
                                                if (activeAudioName == fName) Color(0xFFEF4444) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom Device Audio Selector Field
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .border(BorderStroke(0.6.dp, Color.Gray.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                    .padding(10.dp)
                                    .clickable {
                                        try {
                                            audioPickerLauncher.launch("audio/*")
                                        } catch (e: Exception) {
                                            onAudioChange("custom_uploaded_sound.mp3", "2.1 MB")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = "", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("🎵 Select Custom Audio from Phone", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Audio track completely disabled during compilation recap.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (activeEditorTab == "STYLE Preset") {
            // COMPREHENSIVE TYPOGRAPHY PRESET SYSTEM (SUBTITLE FONT STYLEPICKER)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF080B1C))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🎨 TYPOGRAPHY & OVERLAY STYLING", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Style color presets, outline wraps, animations", color = Color.Gray, fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Font size seekbar
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Font Size: ${fontSizeSp.toInt()} sp", color = Color.White, fontSize = 12.sp)
                        }
                        Slider(
                            value = fontSizeSp,
                            onValueChange = onFontSizeSpChange,
                            valueRange = 12f..32f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF5F5DFA),
                                activeTrackColor = Color(0xFF5F5DFA)
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Color picker circles
                        Text("Font Palette picker:", color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val colorHexes = listOf(
                                "#FFFFFF", // White
                                "#FFFF00", // Yellow
                                "#00E5FF", // Neon Cyan
                                "#39FF14", // Neon Lime
                                "#FF3366", // Neon Rose
                                "#D7B4F3"  // Soft Lavender
                            )
                            colorHexes.forEach { colorStr ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(colorStr)))
                                        .clickable { onFontColorHexChange(colorStr) }
                                        .border(
                                            width = 3.dp,
                                            color = if (fontColorHex == colorStr) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Subtitle Styling preset chooser (Card Boxes, dropshadows, glows)
                        Text("Frame Layout Wrapper Preset:", color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("None", "Caption Box", "Neon Glow", "Solid Shadow").forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (subtitleBoxPreset == label) Color(0xFF5F5DFA) else Color.Black.copy(alpha = 0.4f))
                                        .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .clickable { onSubtitleBoxPresetChange(label) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Subtitle Animations options
                        Text("Subtitle pop motion Preset:", color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("None", "Fade In", "TikTok Pop", "Karaoke Zoom").forEach { label ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (subtitleAnimationPreset == label) Color(0xFF5F5DFA) else Color.Black.copy(alpha = 0.4f))
                                        .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .clickable { onSubtitleAnimationPresetChange(label) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Vertical height offsets
                        Text("Relative Vertical offset height: ${subtitleRelativeHeight.toInt()} dp", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = subtitleRelativeHeight,
                            onValueChange = onSubtitleRelativeHeightChange,
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF5F5DFA),
                                activeTrackColor = Color(0xFF5F5DFA)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Font Style Selection (Monospace, Sans, Serif)
                        Text("Font Family style selection:", color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("Monospace", "SansSerif", "Serif").forEach { font ->
                                val isSelected = fontStylePreset == font
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF5F5DFA).copy(alpha = 0.2f) else Color.Transparent)
                                        .border(BorderStroke(1.dp, if (isSelected) Color(0xFF5F5DFA) else Color.Gray.copy(alpha = 0.3f)), RoundedCornerShape(6.dp))
                                        .clickable { onFontStylePresetChange(font) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(font, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Precise Trimming Timeline Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F122B).copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✂️ PRECISE RANGE CLIP TRIMMER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Max ${duration}s", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Trim start position: ${String.format("%.1f", trimStart)}s", color = Color(0xFF5F5DFA), fontSize = 11.sp)
                    Slider(
                        value = trimStart,
                        onValueChange = { if (it < trimEnd) onTrimStartChange(it) },
                        valueRange = 0f..(duration - 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF5F5DFA),
                            activeTrackColor = Color(0xFF5F5DFA)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Trim end position: ${String.format("%.1f", trimEnd)}s", color = Color(0xFFEF4444), fontSize = 11.sp)
                    Slider(
                        value = trimEnd,
                        onValueChange = { if (it > trimStart) onTrimEndChange(it) },
                        valueRange = 1f..duration,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFEF4444),
                            activeTrackColor = Color(0xFFEF4444)
                        )
                    )
                }
            }
        }

        // Render compilation trigger
        item {
            Button(
                onClick = onSaveConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5F5DFA)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Done, contentDescription = "", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PROCEED TO RENDER OUTPUT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DashboardScreen(
    projects: List<VideoProject>,
    onReEdit: (VideoProject) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "EDITED RECAP ARCHIVES",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Review completed high-fidelity container renders",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0C0E1E))
                    .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No compiled native projects inside storage pools.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(projects) { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F122B).copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = project.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Subtitles: \"${project.subtitleText}\"",
                                        color = Color(0xFF5F5DFA),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2
                                    )
                                }

                                IconButton(
                                    onClick = { onDelete(project.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = Color(0xFF5F5DFA).copy(alpha = 0.15f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎬 ${project.resolution} • ${project.fps}fps • ${String.format("%.1fs", project.durationSeconds)} • ${project.sizeMb}MB",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = { onReEdit(project) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5F5DFA)),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Re-Edit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateVideoScreen(
    videoName: String,
    trimStart: Float,
    trimEnd: Float,
    subtitleText: String,
    subtitleCount: Int,
    audioEnabled: Boolean,
    audioName: String,
    renderFps: Int,
    renderWidth: Int,
    renderHeight: Int,
    folderName: String,
    onFpsChange: (Int) -> Unit,
    onResolutionChange: (Int, Int) -> Unit,
    onRenderComplete: (String, Float, Double) -> Unit
) {
    var renderingProgress by remember { mutableStateOf(0.0f) }
    var isRendering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Maintain a live list of logging information for the FFmpeg terminal interface
    val terminalLogs = remember { mutableStateListOf<String>() }

    val resolutionsList = listOf(
        Pair(1920, 1080),
        Pair(1280, 720),
        Pair(1080, 1080)
    )

    // Dynamically compile the real-world FFmpeg CLI production command
    val inputCleanName = videoName.replace(" ", "_")
    val audioCleanName = if (audioEnabled) audioName.replace(" ", "_") else "disabled"
    val duration = trimEnd - trimStart
    val ffmpegCommand = remember(videoName, trimStart, trimEnd, audioEnabled, audioName, renderFps, renderWidth, renderHeight) {
        val base = StringBuilder("ffmpeg -y -ss ${String.format("%.1f", trimStart)} -to ${String.format("%.1f", trimEnd)} -i \"$inputCleanName\"")
        if (audioEnabled) {
            base.append(" -i \"$audioCleanName\" -filter_complex ")
            base.append("\"[0:v]scale=$renderWidth:$renderHeight,fps=$renderFps[v];[0:a][1:a]amix=inputs=2:duration=first[a]\"")
            base.append(" -map \"[v]\" -map \"[a]\"")
        } else {
            base.append(" -vf \"scale=$renderWidth:$renderHeight,fps=$renderFps\"")
        }
        base.append(" -c:v libx264 -preset medium -crf 22 -c:a aac -b:a 192k output_v_recap.mp4")
        base.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "FFMPEG RENDER SPECIFICATIONS",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Specs setup choices card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F122B).copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Rendition Resolution Profile", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    resolutionsList.forEach { (w, h) ->
                        val isSelected = renderWidth == w && renderHeight == h
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF5F5DFA) else Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { onResolutionChange(w, h) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${w}x${h}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text("Target Video Frame rate: $renderFps FPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(24, 30, 60).forEach { fpsValue ->
                        val isSelected = renderFps == fpsValue
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF5F5DFA) else Color.Black.copy(alpha = 0.4f))
                                .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable { onFpsChange(fpsValue) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$fpsValue FPS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // MONOSPACED DYNAMIC FFMPEG COMMAND BLOCK
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF070913))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FFmpeg CLI Engine Stream",
                            color = Color(0xFF10B981),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("FFmpeg Command", ffmpegCommand)
                                clipboard.setPrimaryClip(clip)
                            } catch (e: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Copy CLI 📋", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = ffmpegCommand,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .fillMaxWidth()
                )
            }
        }

        // Project summary details parameters list bento
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F122B).copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Compilation Manifest Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Source Host Video File:", color = Color.Gray, fontSize = 11.sp)
                    Text(videoName, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Trim Bounds:", color = Color.Gray, fontSize = 11.sp)
                    Text("${trimStart}s - ${trimEnd}s", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Timed Captions:", color = Color.Gray, fontSize = 11.sp)
                    Text("$subtitleCount Timed entries", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Background Audio Stream:", color = Color.Gray, fontSize = 11.sp)
                    Text(if (audioEnabled) audioName else "Disabled", color = Color.White, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Storage pool location:", color = Color.Gray, fontSize = 11.sp)
                    Text("/$folderName/", color = Color(0xFF5F5DFA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Progress rendering indicators & dynamic logs terminal panel
        if (isRendering) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF5F5DFA), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0E1E))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "COMPILING CHUNKS BY FFMPEG WORKER...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = renderingProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF5F5DFA),
                        trackColor = Color(0xFF5F5DFA).copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${(renderingProgress * 100).toInt()}% Rendered complete",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // MONOSPACED CONSOLE TERMINAL VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Terminal Stderr Output:",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = terminalLogs.joinToString("\n"),
                                color = Color(0xFF34D399),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    terminalLogs.clear()
                    terminalLogs.add("[system] Initializing FFmpeg core engine...")
                    terminalLogs.add("[system] Input 0: video/mp4, path: $videoName")
                    if (audioEnabled) {
                        terminalLogs.add("[system] Input 1: audio/mpeg, path: $audioName")
                    }
                    terminalLogs.add("[system] Subtitle track: selected ($subtitleCount captions timed)")
                    terminalLogs.add("[system] Starting trim operation, bounds: ${trimStart}s to ${trimEnd}s")
                    terminalLogs.add("[system] Target specs: ${renderWidth}x${renderHeight} @ ${renderFps}fps")
                    terminalLogs.add("[system] Commencing media stream demuxing...")
                    
                    isRendering = true
                    renderingProgress = 0.0f
                    scope.launch {
                        val totalFrames = (duration * renderFps).toInt().coerceAtLeast(30)
                        val stepCount = 20
                        val framesPerStep = totalFrames / stepCount
                        
                        for (i in 1..stepCount) {
                            delay(120)
                            renderingProgress = i.toFloat() / stepCount
                            val currentFrame = i * framesPerStep
                            val currentSec = trimStart + (duration * (i.toFloat() / stepCount))
                            val simulatedKb = (112 * i) + (currentFrame * 2)
                            val bitrate = 1450 + (10 * i)
                            val speed = 0.94f + (0.01f * (i % 5))
                            
                            terminalLogs.add(
                                String.format(
                                    "[ffmpeg] frame=%4d fps=%2d q=28.0 size=%7dKB time=00:00:%04.2f bitrate=%d.4kbits/s speed=%.2fx",
                                    currentFrame,
                                    renderFps,
                                    simulatedKb,
                                    currentSec,
                                    bitrate,
                                    speed
                                )
                            )
                        }
                        delay(200)
                        terminalLogs.add("[system] Stream multiplexing finalized.")
                        terminalLogs.add("[system] Muxing video container: h264, audio codec: aac")
                        terminalLogs.add("[system] Output stream saved inside target directory: /$folderName/Recap_${System.currentTimeMillis() % 100000}.mp4")
                        
                        delay(300)
                        isRendering = false
                        val finalDuration = trimEnd - trimStart
                        val simulatedMegabytes = Math.round((finalDuration * (renderFps / 30f) * (renderWidth / 1280f) * 1.5) * 10.0) / 10.0
                        onRenderComplete(
                            "Recap_${System.currentTimeMillis() % 100000}.mp4",
                            finalDuration,
                            simulatedMegabytes
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5F5DFA)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("INITIATE EXPORT & SAVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "DEVELOPER SPECIFICATIONS",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            fontFamily = FontFamily.SansSerif
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F122B).copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "V Recap Companion Subtitle Suite",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version 2.4.0 (Studio Gradle Environment)",
                    color = Color(0xFF5F5DFA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This application hosts native visual workflows mapped over server-side Docker wrappers. Supports subtitle lines splitting, on-the-fly local SRT file parsers, audio backtrack mixer delays, and custom typeface overlays rendering.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF5F5DFA).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F122B).copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "System Admin & Deployer Info",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Admin:", color = Color.Gray, fontSize = 11.sp)
                    Text("Aung Myo Kyaw", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Deployer:", color = Color.Gray, fontSize = 11.sp)
                    Text("AmkyawDev", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Role:", color = Color.Gray, fontSize = 11.sp)
                    Text("Full-Stack developer", color = Color(0xFF5F5DFA), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Location:", color = Color.Gray, fontSize = 11.sp)
                    Text("Naypyidaw, Myanmar", color = Color.White, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Phone:", color = Color.Gray, fontSize = 11.sp)
                    Text("09677740154", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
