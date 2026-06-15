package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var activeVideoName by remember { mutableStateOf("sunset_drive.mp4") }
    var trimStart by remember { mutableStateOf(0.0f) }
    var trimEnd by remember { mutableStateOf(15.0f) }
    var videoDuration by remember { mutableStateOf(30.0f) }
    var subtitleText by remember { mutableStateOf("Welcome to my travel vlog!") }
    var audioTrackEnabled by remember { mutableStateOf(true) }
    var renderFps by remember { mutableStateOf(60) }
    var renderWidth by remember { mutableStateOf(1920) }
    var renderHeight by remember { mutableStateOf(1080) }

    // Projects list with sample data
    var projectsList by remember {
        mutableStateOf(
            listOf(
                VideoProject("1", "Summer_Trip_Recap.mp4", 12.5f, "1920x1080", 60, "2026-06-12", 45.3, 0f, 12.5f, "Exploring the beaches"),
                VideoProject("2", "Cooking_Tutorial_v2.mp4", 45.0f, "1280x720", 30, "2026-06-14", 112.1, 5f, 40f, "Add one tablespoon of salt"),
                VideoProject("3", "Product_Intro_Ad.mp4", 15.0f, "1080x1080", 60, "2026-06-15", 32.5, 0f, 15f, "Get 20% off today!")
            )
        )
    }

    val gradientBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0B132B), // Very dark navy
            Color(0xFF1C2541), // Rich slate deep blue
            Color(0xFF0E1424)
        )
    )

    // Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Top Bar (Except GetStarted Screen)
            if (currentScreen != Screen.GET_STARTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(Color(0xFF0B132B).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { isDrawerOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Drawer Menu",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = when (currentScreen) {
                            Screen.MAIN_HOME -> "V RECAP HOME"
                            Screen.VIDEO_EDITING -> "VIDEO WORKSPACE"
                            Screen.DASHBOARD -> "PROJECT DASHBOARD"
                            Screen.CREATE_VIDEO -> "RENDER ENGINE"
                            Screen.ABOUT -> "INFO & SETTINGS"
                            else -> "V RECAP"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp
                    )

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Active Indicator",
                        tint = Color(0xFFE53E3E),
                        modifier = Modifier.size(24.dp)
                    )
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
                        onNavigate = { currentScreen = it }
                    )
                    Screen.VIDEO_EDITING -> VideoEditingScreen(
                        videoName = activeVideoName,
                        trimStart = trimStart,
                        trimEnd = trimEnd,
                        duration = videoDuration,
                        subtitleText = subtitleText,
                        audioEnabled = audioTrackEnabled,
                        onVideoNameChange = { activeVideoName = it },
                        onTrimStartChange = { trimStart = it },
                        onTrimEndChange = { trimEnd = it },
                        onSubtitleChange = { subtitleText = it },
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
                            subtitleText = proj.subtitleText
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
                        subtitleText = subtitleText,
                        audioEnabled = audioTrackEnabled,
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
                                subtitleText = subtitleText
                            )
                            projectsList = listOf(newProj) + projectsList
                            currentScreen = Screen.DASHBOARD
                        }
                    )
                    Screen.ABOUT -> AboutScreen()
                }
            }
        }

        // Custom Sliding Side Menu Drawer Drawer (overlay)
        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isDrawerOpen = false }
            ) {
                // Outer Box Click-catcher, Inner box is Drawer Content
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .background(Color(0xFF0F172A))
                        .border(
                            width = 1.dp,
                            color = Color(0xFFE53E3E),
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
                            // Drawer Header
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "V RECAP SUITE",
                                    color = Color(0xFFE53E3E),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 20.sp
                                )
                                IconButton(onClick = { isDrawerOpen = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Menu",
                                        tint = Color.White
                                        )
                                }
                            }

                            Divider(color = Color(0xFFE53E3E).copy(alpha = 0.4f), thickness = 1.dp, modifier = Modifier.padding(bottom = 16.dp))

                            // Menu Items
                            DrawerItem(Icons.Default.Home, "Workspace Home", isSelected = currentScreen == Screen.MAIN_HOME) {
                                currentScreen = Screen.MAIN_HOME
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.PlayArrow, "Video Timeline", isSelected = currentScreen == Screen.VIDEO_EDITING) {
                                currentScreen = Screen.VIDEO_EDITING
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.List, "Saved Dashboard", isSelected = currentScreen == Screen.DASHBOARD) {
                                currentScreen = Screen.DASHBOARD
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.Settings, "Render & Export", isSelected = currentScreen == Screen.CREATE_VIDEO) {
                                currentScreen = Screen.CREATE_VIDEO
                                isDrawerOpen = false
                            }
                            DrawerItem(Icons.Default.Info, "About / Admin Support", isSelected = currentScreen == Screen.ABOUT) {
                                currentScreen = Screen.ABOUT
                                isDrawerOpen = false
                            }
                        }

                        // App Status Info
                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(text = "App Mode: Full Native Debug", color = Color.Gray, fontSize = 12.sp)
                            Text(text = "Active Folder: ${folderName}", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Logout",
                                modifier = Modifier
                                    .clickable {
                                        currentScreen = Screen.GET_STARTED
                                        isDrawerOpen = false
                                    },
                                color = Color(0xFFE53E3E),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColors = if (isSelected) Color(0xFFE53E3E).copy(alpha = 0.15f) else Color.Transparent
    val borderCol = if (isSelected) Color(0xFFE53E3E) else Color.Transparent

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
            tint = if (isSelected) Color(0xFFE53E3E) else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = if (isSelected) Color(0xFFE53E3E) else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp
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
                .border(2.dp, Color(0xFFE53E3E), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Permission Alert",
                    tint = Color(0xFFE53E3E),
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "V RECAP CONFIGURATION",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "System Permissions Required: This applet operates video-processing resources that need folder access configured on compilation build.",
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
                    label = { Text("Local Folder Name", color = Color(0xFFFC8181)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFE53E3E),
                        unfocusedBorderColor = Color(0xFFE53E3E).copy(alpha = 0.5f),
                        focusedLabelColor = Color(0xFFFC8181),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE53E3E).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFE53E3E), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "ALERT: Local cache folder will contain high-performance edited Android frame files.",
                        color = Color(0xFFFC8181),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Left
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onGranted,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "GRANT PERMISSIONS & CONTINUE",
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
    onNavigate: (Screen) -> Unit
) {
    var animatedText by remember { mutableStateOf("") }
    val fullText = "Welcome to V Recap - Professional Video Editing Suite"

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
            // Animating Headline Title
            Text(
                text = "$animatedText|",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 36.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .height(80.dp) // Maintain space during typing
            )

            Text(
                text = "Studio Grade Android Video Recaps & AI Trimming with Full Subtitle Font Customization",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 32.dp)
            )

            // Grid options custom implementation (no column grid complexity)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeMenuItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Video Editor WorkSpace",
                    description = "Interactive timeline, multi-resolution, custom caption overlays.",
                    onClick = { onNavigate(Screen.VIDEO_EDITING) }
                )

                HomeMenuItem(
                    icon = Icons.Default.List,
                    title = "Exported Saved Dashboard",
                    description = "Historical project lists, detail parameters, re-editing configs.",
                    onClick = { onNavigate(Screen.DASHBOARD) }
                )

                HomeMenuItem(
                    icon = Icons.Default.Info,
                    title = "App Info & Support",
                    description = "Full technical details, directory logs, and developers specifications.",
                    onClick = { onNavigate(Screen.ABOUT) }
                )
            }
        }
    }
}

@Composable
fun HomeMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222B45))
            .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE53E3E).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    tint = Color(0xFFE53E3E),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFFA0AEC0),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Go",
                tint = Color(0xFFE53E3E).copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun VideoEditingScreen(
    videoName: String,
    trimStart: Float,
    trimEnd: Float,
    duration: Float,
    subtitleText: String,
    audioEnabled: Boolean,
    onVideoNameChange: (String) -> Unit,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    onSubtitleChange: (String) -> Unit,
    onAudioToggle: (Boolean) -> Unit,
    onSaveConfig: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Video Preview Card / Video Placeholder
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .border(2.dp, Color(0xFFE53E3E), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Accent dynamic subtitle rendering onto the mockup
                    Text(
                        text = "VIDEO SCREEN: $videoName",
                        color = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )

                    // Video content mockup
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play icon",
                            tint = Color(0xFFE53E3E),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Trimming: [${String.format("%.1f", trimStart)}s - ${String.format("%.1f", trimEnd)}s]",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    // Burned/Mocked Subtitles on the Video screen itself:
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (subtitleText.isNotEmpty()) "\"$subtitleText\"" else "- No Subtitles -",
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            // Video File Selector
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Target Project Resource", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = videoName,
                        onValueChange = onVideoNameChange,
                        label = { Text("Video Asset Host", color = Color(0xFFFC8181)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53E3E),
                            unfocusedBorderColor = Color(0xFFE53E3E).copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        item {
            // Trimming Timeline Tools
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trimmer Range Editor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Total Duration: 30.0s",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Trim Start Time: ${String.format("%.1f", trimStart)}s", color = Color(0xFFFC8181), fontSize = 12.sp)
                    Slider(
                        value = trimStart,
                        onValueChange = { if (it < trimEnd) onTrimStartChange(it) },
                        valueRange = 0f..29f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE53E3E),
                            activeTrackColor = Color(0xFFE53E3E),
                            inactiveTrackColor = Color.LightGray.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Trim End Time: ${String.format("%.1f", trimEnd)}s", color = Color(0xFFFC8181), fontSize = 12.sp)
                    Slider(
                        value = trimEnd,
                        onValueChange = { if (it > trimStart) onTrimEndChange(it) },
                        valueRange = 1f..30f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFE53E3E),
                            activeTrackColor = Color(0xFFE53E3E),
                            inactiveTrackColor = Color.LightGray.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        item {
            // Subtitles and Caption Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subtitle Streaming Overlay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = subtitleText,
                        onValueChange = onSubtitleChange,
                        label = { Text("Caption Text", color = Color(0xFFFC8181)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE53E3E),
                            unfocusedBorderColor = Color(0xFFE53E3E).copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        item {
            // Audio Overlays Toggle Settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Add Audio Backing Track", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Synthesize automatic backing vlog voice-overs", color = Color.Gray, fontSize = 11.sp)
                    }

                    Switch(
                        checked = audioEnabled,
                        onCheckedChange = onAudioToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFE53E3E)
                        )
                    )
                }
            }
        }

        item {
            // Save settings proceed button
            Button(
                onClick = onSaveConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("PROCEED TO RENDER OUTPUT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
            text = "EDITED PROJECTS ARCHIVE",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF222B45))
                    .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No compiled projects on record.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects) { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
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
                                    Text(
                                        text = "Sub: \"${project.subtitleText}\"",
                                        color = Color(0xFFFC8181),
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { onDelete(project.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Specs list mini
                                Text(
                                    text = "${project.resolution} • ${project.fps}fps • ${String.format("%.1f", project.durationSeconds)}s • ${project.sizeMb}MB",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Button(
                                    onClick = { onReEdit(project) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E)),
                                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
                                    shape = RoundedCornerShape(4.dp),
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
    audioEnabled: Boolean,
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

    // Video Output Formats state selector
    val resolutionsList = listOf(
        Pair(1920, 1080),
        Pair(1280, 720),
        Pair(1080, 1080)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "RENDER CONTROLLER CONFIGS",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Settings config panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Rendition Specs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Resolution Choice Row
                Text("Target Video Resolution:", color = Color(0xFFFC8181), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    resolutionsList.forEach { (w, h) ->
                        val isSelected = renderWidth == w && renderHeight == h
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFFE53E3E) else Color.Transparent)
                                .border(1.dp, Color(0xFFE53E3E), RoundedCornerShape(6.dp))
                                .clickable { onResolutionChange(w, h) }
                                .padding(vertical = 10.dp),
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

                Spacer(modifier = Modifier.height(16.dp))

                // Frame rate Choice Row
                Text("Target Frame Rate: $renderFps FPS", color = Color(0xFFFC8181), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(24, 30, 60).forEach { fpsValue ->
                        val isSelected = renderFps == fpsValue
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFFE53E3E) else Color.Transparent)
                                .border(1.dp, Color(0xFFE53E3E), RoundedCornerShape(6.dp))
                                .clickable { onFpsChange(fpsValue) }
                                .padding(vertical = 10.dp),
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

        // Active compile summary
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Output Compilation Summary", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Source File:", color = Color.Gray, fontSize = 12.sp)
                    Text(videoName, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Trimming Timeline:", color = Color.Gray, fontSize = 12.sp)
                    Text("${trimStart}s - ${trimEnd}s", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Burned Overlay Caption:", color = Color.Gray, fontSize = 12.sp)
                    Text(subtitleText, color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sub-Audio voice overs:", color = Color.Gray, fontSize = 12.sp)
                    Text(if (audioEnabled) "Enabled" else "Off", color = Color.White, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target Folder:", color = Color.Gray, fontSize = 12.sp)
                    Text("/$folderName/", color = Color(0xFFFC8181), fontSize = 12.sp)
                }
            }
        }

        // Render Action / Progress panel
        if (isRendering) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFFE53E3E), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "COMPILING VIDEO CHUNKS WITH FFmpeg...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = renderingProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFFE53E3E),
                        trackColor = Color.LightGray.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${(renderingProgress * 100).toInt()}% Render Completed",
                        color = Color(0xFFFC8181),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    isRendering = true
                    renderingProgress = 0.0f
                    scope.launch {
                        while (renderingProgress < 1.0f) {
                            delay(100)
                            renderingProgress += 0.05f
                        }
                        delay(200)
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
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("INITIATE DOCKER COMPILED EXPORT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
            text = "ABOUT THE RECAP SUITE",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "V Recap Companion Application",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version 1.0.0 (Gradle Production Assembly)",
                    color = Color(0xFFFC8181),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This application provides fully visual controls mapped directly over server-side video compilers and ffmpeg tools. It compiles fast, offline-supportive recap chunks complete with custom-rendered audio layers and captions.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE53E3E).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222B45))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Support Details & Infrastructure",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Target platform:", color = Color.Gray, fontSize = 12.sp)
                    Text("Android S+ SDK 36", color = Color.White, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Compiler core:", color = Color.Gray, fontSize = 12.sp)
                    Text("Jetpack Compose Native", color = Color.White, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Admin email:", color = Color.Gray, fontSize = 12.sp)
                    Text("aung.thuyrain.at449@gmail.com", color = Color(0xFFFC8181), fontSize = 12.sp)
                }
            }
        }
    }
}
