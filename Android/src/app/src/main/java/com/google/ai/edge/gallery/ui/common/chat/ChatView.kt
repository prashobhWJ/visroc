/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat

// import com.google.ai.edge.gallery.ui.preview.PreviewChatModel
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
// import com.google.ai.edge.gallery.ui.preview.TASK_TEST1
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.data.TaskType
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.proto.ImportedModel
import com.google.ai.edge.gallery.ui.common.ModelPageAppBar
import com.google.ai.edge.gallery.ui.home.ModelImportDialog
import com.google.ai.edge.gallery.ui.home.ModelImportingDialog
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.modelmanager.PagerScrollState
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGChatView"

/**
 * A composable that displays a chat interface, allowing users to interact with different models
 * associated with a given task.
 *
 * This composable provides a horizontal pager for switching between models, a model selector for
 * configuring the selected model, and a chat panel for sending and receiving messages. It also
 * manages model initialization, cleanup, and download status, and handles navigation and system
 * back gestures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
  task: Task,
  viewModel: ChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  onSendMessage: (Model, List<ChatMessage>) -> Unit,
  onRunAgainClicked: (Model, ChatMessage) -> Unit,
  onBenchmarkClicked: (Model, ChatMessage, Int, Int) -> Unit,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onResetSessionClicked: (Model) -> Unit = {},
  onStreamImageMessage: (Model, ChatMessageImage) -> Unit = { _, _ -> },
  onStopButtonClicked: (Model) -> Unit = {},
  chatInputType: ChatInputType = ChatInputType.TEXT,
  showStopButtonInInputWhenInProgress: Boolean = false,
  onRobotCommand: ((String, String) -> Unit)? = null, // robotIP, command
  onAIResponseComplete: ((String) -> Unit)? = null, // AI response callback
) {
  val uiState by viewModel.uiState.collectAsState()
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel
  var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
  var showImageViewer by remember { mutableStateOf(false) }

  // Import model state variables
  var showImportModelSheet by remember { mutableStateOf(false) }
  var showUnsupportedFileTypeDialog by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState()
  var showImportDialog by remember { mutableStateOf(false) }
  var showImportingDialog by remember { mutableStateOf(false) }
  val selectedLocalModelFileUri = remember { mutableStateOf<Uri?>(null) }
  val selectedImportedModelInfo = remember { mutableStateOf<ImportedModel?>(null) }
  val snackbarHostState = remember { SnackbarHostState() }
  
  // Robot control specific state
  var robotIpAddress by remember { mutableStateOf("192.168.1.100") }
  var showRobotConfig by remember { mutableStateOf(false) }
  
  // Camera capture for robot control
  var lastCaptureTime by remember { mutableStateOf(0L) }
  val cameraCaptureInterval = 10000L // 10 seconds
  
  // Monitor AI responses for robot actions (Control Robot task only)
  LaunchedEffect(uiState.messagesByModel, selectedModel, task.type, uiState.inProgress) {
    if (task.type == TaskType.LLM_ASK_IMAGE && onRobotCommand != null) {
      val messages = uiState.messagesByModel[selectedModel.name] ?: listOf()
      val latestMessage = messages.lastOrNull()
      if (latestMessage is ChatMessageText && latestMessage.side == ChatSide.AGENT) {
        // Check if this is a new complete response (not streaming)
        if (!uiState.inProgress && latestMessage.content.isNotEmpty()) {
          Log.d("RobotControl", "Processing AI response: ${latestMessage.content}")
          extractAndSendJsonAction(latestMessage.content, robotIpAddress, onRobotCommand)
        }
      }
    }
  }

  val pagerState =
    rememberPagerState(
      initialPage = if (task.models.isNotEmpty()) task.models.indexOf(selectedModel) else 0,
      pageCount = { maxOf(task.models.size, 1) }, // Ensure at least 1 page to prevent crashes
    )
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var navigatingUp by remember { mutableStateOf(false) }

  val filePickerLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          val fileName = getFileName(context = context, uri = uri)
          Log.d(TAG, "Selected file: $fileName")
          if (fileName != null && !fileName.endsWith(".task")) {
            showUnsupportedFileTypeDialog = true
          } else {
            selectedLocalModelFileUri.value = uri
            showImportDialog = true
          }
        } ?: run { Log.d(TAG, "No file selected or URI is null.") }
      } else {
        Log.d(TAG, "File picking cancelled.")
      }
    }

  val handleNavigateUp = {
    navigatingUp = true
    navigateUp()

    // clean up all models.
    scope.launch(kotlinx.coroutines.Dispatchers.Default) {
      for (model in task.models) {
        modelManagerViewModel.cleanupModel(task = task, model = model)
      }
    }
  }

  // Initialize model when model/download state changes.
  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (!navigatingUp) {
      Log.d(TAG, "Download status for model '${selectedModel.name}': ${curDownloadStatus?.status}")
      if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
        Log.d(TAG, "Initializing model '${selectedModel.name}' from ChatView launched effect")
        modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
      }
    }
  }



  // Update selected model and clean up previous model when page is settled on a model page.
  LaunchedEffect(pagerState.settledPage) {
    val curSelectedModel = task.models[pagerState.settledPage]
    Log.d(
      TAG,
      "Pager settled on model '${curSelectedModel.name}' from '${selectedModel.name}'. Updating selected model.",
    )
    if (curSelectedModel.name != selectedModel.name) {
      modelManagerViewModel.cleanupModel(task = task, model = selectedModel)
    }
    modelManagerViewModel.selectModel(curSelectedModel)
  }

  LaunchedEffect(pagerState) {
    // Collect from the a snapshotFlow reading the currentPage
    snapshotFlow { pagerState.currentPage }.collect { page -> Log.d(TAG, "Page changed to $page") }
  }

  // Trigger scroll sync.
  LaunchedEffect(pagerState) {
    snapshotFlow {
        PagerScrollState(
          page = pagerState.currentPage,
          offset = pagerState.currentPageOffsetFraction,
        )
      }
      .collect { scrollState -> modelManagerViewModel.pagerScrollState.value = scrollState }
  }

  // Handle system's edge swipe.
  BackHandler { handleNavigateUp() }

  Scaffold(
    modifier = modifier,
    topBar = {
      ModelPageAppBar(
        task = task,
        model = selectedModel,
        modelManagerViewModel = modelManagerViewModel,
        canShowResetSessionButton = true,
        isResettingSession = uiState.isResettingSession,
        inProgress = uiState.inProgress,
        modelPreparing = uiState.preparing,
        onResetSessionClicked = onResetSessionClicked,
        onConfigChanged = { old, new ->
          viewModel.addConfigChangedMessage(
            oldConfigValues = old,
            newConfigValues = new,
            model = selectedModel,
          )
        },
        onBackClicked = { handleNavigateUp() },
        onModelSelected = { model ->
          scope.launch { pagerState.animateScrollToPage(task.models.indexOf(model)) }
        },
      )
    },
    floatingActionButton = {
      // Only show import model FAB for tasks other than Control Robot
      if (task.type != TaskType.LLM_ASK_IMAGE) {
        SmallFloatingActionButton(
          onClick = { showImportModelSheet = true },
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.secondary,
        ) {
          Icon(Icons.Filled.Add, "")
        }
      }
    },
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(bottom = 32.dp),
      )
    },
  ) { innerPadding ->
    Box {
      // A horizontal scrollable pager to switch between models.
      HorizontalPager(state = pagerState) { pageIndex ->
        // Handle empty models case for Control Robot
        if (task.models.isEmpty() && task.type == TaskType.LLM_ASK_IMAGE) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp),
              modifier = Modifier.padding(32.dp)
            ) {
              Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                "No image models available",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
              Text(
                "Import a model with image support to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
          return@HorizontalPager
        }
        
        val curSelectedModel = task.models[pageIndex]
        val curModelDownloadStatus = modelManagerUiState.modelDownloadStatus[curSelectedModel.name]

        // Calculate the alpha of the current page based on how far they are from the center.
        val pageOffset =
          ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
            .absoluteValue
        val curAlpha = 1f - pageOffset.coerceIn(0f, 1f)

        Column(
          modifier =
            Modifier.padding(innerPadding)
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surface)
        ) {
          // Only show download panel for tasks other than Control Robot
          if (task.type != TaskType.LLM_ASK_IMAGE) {
            ModelDownloadStatusInfoPanel(
              model = curSelectedModel,
              task = task,
              modelManagerViewModel = modelManagerViewModel,
            )
          } else {
            // Show robot configuration for Control Robot task
            RobotControlConfig(
              robotIpAddress = robotIpAddress,
              onIpAddressChange = { robotIpAddress = it },
              modifier = Modifier.padding(16.dp),
              onSendPredictionPrompt = {
                val predictionPrompt = "Analyze the image uploaded by user. If no image is available, please request to upload one concisely. Based on the robot's current state, predict the next logical action it should take. Output the prediction as JSON with just one action, Example JSON { \"action\": \"Extend gripper\". Valid actions are  'extend_gripper', 'retract_gripper', 'open_claw', 'close_claw', 'turn_table_left', 'turn_table_right', 'move_arms_up', 'move_arms_down', 'dance' . Use these only as message values for actions. Single line for JSON, do not split into multiple lines}"
                val messages = listOf(ChatMessageText(content = predictionPrompt, side = ChatSide.USER))
                onSendMessage(selectedModel, messages)
              }
            )
          }

          // The main messages panel.
          // For Control Robot, only show if model is already downloaded or imported
          // For other tasks, show the existing download logic
          val shouldShowMainPanel = if (task.type == TaskType.LLM_ASK_IMAGE) {
            curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
          } else {
            curModelDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
          }
          
          if (shouldShowMainPanel) {
            ChatPanel(
              modelManagerViewModel = modelManagerViewModel,
              task = task,
              selectedModel = curSelectedModel,
              viewModel = viewModel,
              navigateUp = navigateUp,
              onSendMessage = { model, messages ->
                onSendMessage(model, messages)
                
                // For Control Robot task, also send robot commands based on user input
                if (task.type == TaskType.LLM_ASK_IMAGE && onRobotCommand != null) {
                  val userText = messages.filterIsInstance<ChatMessageText>().firstOrNull()?.content
                  userText?.let { text ->
                    extractMovementCommand(text)?.let { command ->
                      Log.d("RobotControl", "Sending direct command: $command")
                      onRobotCommand(robotIpAddress, command)
                    }
                  }
                }
              },
              onRunAgainClicked = onRunAgainClicked,
              onBenchmarkClicked = onBenchmarkClicked,
              onStreamImageMessage = onStreamImageMessage,
              onStreamEnd = { averageFps ->
                viewModel.addMessage(
                  model = curSelectedModel,
                  message =
                    ChatMessageInfo(content = "Live camera session ended. Average FPS: $averageFps"),
                )
              },
              onStopButtonClicked = { onStopButtonClicked(curSelectedModel) },
              onImageSelected = { bitmap ->
                selectedImage = bitmap
                showImageViewer = true
              },
              modifier = Modifier.weight(1f).graphicsLayer { alpha = curAlpha },
              chatInputType = chatInputType,
              showStopButtonInInputWhenInProgress = showStopButtonInInputWhenInProgress,
            )
          } else if (task.type == TaskType.LLM_ASK_IMAGE) {
            // Show message for Control Robot when no models are available
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
              ) {
                Icon(
                  Icons.Outlined.CloudOff,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  "No robot control models available",
                  style = MaterialTheme.typography.headlineSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
                Text(
                  "Import a model with image and audio support to control robots",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        }
      }

      // Image viewer.
      AnimatedVisibility(
        visible = showImageViewer,
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut(),
      ) {
        selectedImage?.let { image ->
          ZoomableBox(
            modifier =
              Modifier.fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(Color.Black.copy(alpha = 0.95f))
          ) {
            Image(
              bitmap = image.asImageBitmap(),
              contentDescription = "",
              modifier =
                modifier
                  .fillMaxSize()
                  .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                  ),
              contentScale = ContentScale.Fit,
            )

            // Close button.
            IconButton(
              onClick = { showImageViewer = false },
              colors =
                IconButtonDefaults.iconButtonColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
              modifier = Modifier.offset(x = (-8).dp, y = 8.dp),
            ) {
              Icon(
                Icons.Rounded.Close,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
        }
      }
    }

    // Import model bottom sheet.
    if (showImportModelSheet) {
      ModalBottomSheet(onDismissRequest = { showImportModelSheet = false }, sheetState = sheetState) {
        Text(
          "Import model",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
        )
        Box(
          modifier =
            Modifier.clickable {
              scope.launch {
                // Give it sometime to show the click effect.
                delay(200)
                showImportModelSheet = false

                // Show file picker.
                val intent =
                  Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    // Single select.
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                  }
                filePickerLauncher.launch(intent)
              }
            }
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
          ) {
            Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = "")
            Text("From local model file")
          }
        }
      }
    }

    // Import dialog
    if (showImportDialog) {
      selectedLocalModelFileUri.value?.let { uri ->
        ModelImportDialog(
          uri = uri,
          onDismiss = { showImportDialog = false },
          onDone = { info ->
            selectedImportedModelInfo.value = info
            showImportDialog = false
            showImportingDialog = true
          },
          defaultSupportImage = true  // Default to true when importing from Control Robot page
        )
      }
    }

    // Importing in progress dialog.
    if (showImportingDialog) {
      selectedLocalModelFileUri.value?.let { uri ->
        selectedImportedModelInfo.value?.let { info ->
          ModelImportingDialog(
            uri = uri,
            info = info,
            onDismiss = { showImportingDialog = false },
            onDone = {
              Log.d(TAG, "Importing model with image support: ${it.llmConfig.supportImage}")
              modelManagerViewModel.addImportedLlmModel(info = it)
              showImportingDialog = false

              // Show a snack bar for successful import.
              scope.launch { snackbarHostState.showSnackbar("Model imported successfully") }
            },
          )
        }
      }
    }

    // Alert dialog for unsupported file type.
    if (showUnsupportedFileTypeDialog) {
      AlertDialog(
        onDismissRequest = { showUnsupportedFileTypeDialog = false },
        title = { Text("Unsupported file type") },
        text = { Text("Only \".task\" file type is supported.") },
        confirmButton = {
          Button(onClick = { showUnsupportedFileTypeDialog = false }) {
            Text(stringResource(R.string.ok))
          }
        },
      )
    }
  }
}

// @Preview
// @Composable
// fun ChatScreenPreview() {
//   GalleryTheme {
//     val context = LocalContext.current
//     val task = TASK_TEST1
//     ChatView(
//       task = task,
//       viewModel = PreviewChatModel(context = context),
//       modelManagerViewModel = PreviewModelManagerViewModel(context = context),
//       onSendMessage = { _, _ -> },
//       onRunAgainClicked = { _, _ -> },
//       onBenchmarkClicked = { _, _, _, _ -> },
//       navigateUp = {},
//     )
//   }
// }

@Composable
private fun RobotControlConfig(
  robotIpAddress: String,
  onIpAddressChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  onSendPredictionPrompt: (() -> Unit)? = null
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    )
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          Icons.Outlined.SmartToy,
          contentDescription = "Robot Control",
          tint = MaterialTheme.colorScheme.primary
        )
        Text(
          "Robot Control Settings",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      
      OutlinedTextField(
        value = robotIpAddress,
        onValueChange = onIpAddressChange,
        label = { Text("Robot IP Address") },
        placeholder = { Text("192.168.1.100") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )
      
      Text(
        "Enter the IP address of your robot. AI predictions will automatically send commands to this address.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      
      // Send button for movement prediction
      onSendPredictionPrompt?.let { sendPrompt ->
        Button(
          onClick = sendPrompt,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
          )
        ) {
          Icon(
            Icons.Rounded.Send,
            contentDescription = "Send prediction prompt",
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Analyze Robot & Predict Action")
        }
      }
    }
  }
}

// Function to send robot control commands
suspend fun sendRobotCommand(ipAddress: String, command: String): Boolean {
  return withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
      val fullUrl = "http://$ipAddress:8080"
      Log.d("RobotControl", "Attempting to connect to: $fullUrl")
      
      val url = URL(fullUrl)
      val connection = url.openConnection() as HttpURLConnection
      
      connection.requestMethod = "POST"
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setRequestProperty("Accept", "application/json")
      connection.doOutput = true
      connection.connectTimeout = 5000 // 5 seconds timeout
      connection.readTimeout = 5000 // 5 seconds timeout
      
      val jsonObject = JSONObject()
      jsonObject.put("action", command)
      val jsonString = jsonObject.toString()
      
      Log.d("RobotControl", "Sending JSON payload: $jsonString")
      
      val outputStreamWriter = OutputStreamWriter(connection.outputStream)
      outputStreamWriter.write(jsonString)
      outputStreamWriter.flush()
      outputStreamWriter.close()
      
      val responseCode = connection.responseCode
      Log.d("RobotControl", "Response code: $responseCode")
      
      // Try to read response for more debugging
      try {
        val responseMessage = connection.responseMessage
        Log.d("RobotControl", "Response message: $responseMessage")
      } catch (e: Exception) {
        Log.d("RobotControl", "Could not read response message: ${e.message}")
      }
      
      connection.disconnect()
      val success = responseCode == HttpURLConnection.HTTP_OK
      Log.d("RobotControl", "Command send ${if (success) "successful" else "failed"}")
      success
    } catch (e: Exception) {
      Log.e("RobotControl", "Failed to send command to robot at $ipAddress:8080: ${e.message}")
      Log.e("RobotControl", "Exception type: ${e.javaClass.simpleName}")
      e.printStackTrace()
      false
    }
  }
}

// Function to extract movement commands from AI response
fun extractMovementCommand(aiResponse: String): String? {
  val response = aiResponse.lowercase()
  return when {
    response.contains("forward") || response.contains("move forward") || response.contains("go forward") -> "forward"
    response.contains("backward") || response.contains("move backward") || response.contains("go backward") || response.contains("back") -> "backward"
    response.contains("left") || response.contains("turn left") || response.contains("go left") -> "left"
    response.contains("right") || response.contains("turn right") || response.contains("go right") -> "right"
    response.contains("stop") || response.contains("halt") || response.contains("pause") -> "stop"
    response.contains("rotate") && response.contains("clockwise") -> "rotate_cw"
    response.contains("rotate") && response.contains("counterclockwise") -> "rotate_ccw"
    else -> null
  }
}

// Function to extract JSON action from AI response and send to robot
fun extractAndSendJsonAction(aiResponse: String, robotIP: String, onRobotCommand: (String, String) -> Unit) {
  try {
    // Look for JSON pattern in the response
    val jsonPattern = """\{[^{}]*"action"\s*:\s*"([^"]+)"[^{}]*\}""".toRegex()
    val matchResult = jsonPattern.find(aiResponse)
    
    if (matchResult != null) {
      // Extract the action value
      val action = matchResult.groupValues[1]
      Log.d("RobotControl", "Extracted JSON action: $action from AI response")
      
      // Send the action to robot
      onRobotCommand(robotIP, action)
    } else {
      // Fallback: try to parse as JSON object
      val jsonStart = aiResponse.indexOf("{")
      val jsonEnd = aiResponse.lastIndexOf("}") + 1
      
      if (jsonStart != -1 && jsonEnd > jsonStart) {
        val jsonString = aiResponse.substring(jsonStart, jsonEnd)
        val jsonObject = JSONObject(jsonString)
        
        if (jsonObject.has("action")) {
          val action = jsonObject.getString("action")
          Log.d("RobotControl", "Parsed JSON action: $action")
          onRobotCommand(robotIP, action)
        }
      }
    }
  } catch (e: Exception) {
    Log.e("RobotControl", "Failed to extract JSON action from AI response: ${e.message}")
  }
}

// Helper function to get the file name from a URI
fun getFileName(context: Context, uri: Uri): String? {
  if (uri.scheme == "content") {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1) {
          return cursor.getString(nameIndex)
        }
      }
    }
  } else if (uri.scheme == "file") {
    return uri.lastPathSegment
  }
  return null
}
