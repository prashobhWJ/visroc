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

package com.google.ai.edge.gallery.ui.common

// import androidx.compose.ui.tooling.preview.Preview
// import com.google.ai.edge.gallery.ui.preview.PreviewModelManagerViewModel
// import com.google.ai.edge.gallery.ui.preview.TASK_TEST1
// import com.google.ai.edge.gallery.ui.theme.GalleryTheme

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.modelitem.StatusIcon
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerUiState
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.ui.theme.labelSmallNarrow

@Composable
fun ModelPicker(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onModelSelected: (Model) -> Unit,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()

  // Separate online and imported models
  val onlineModels by
    remember(task) {
      derivedStateOf {
        val trigger = task.updateTrigger.value
        if (trigger >= 0) {
          val online = task.models.toList().filter { !it.imported }
          Log.d("ModelPicker", "Task: ${task.type.label}, Online models: ${online.size}")
          online
        } else {
          listOf()
        }
      }
    }
  val importedModels by
    remember(task) {
      derivedStateOf {
        val trigger = task.updateTrigger.value
        if (trigger >= 0) {
          val imported = task.models.toList().filter { it.imported }
          Log.d("ModelPicker", "Task: ${task.type.label}, Imported models: ${imported.size}")
          for (model in imported) {
            Log.d("ModelPicker", "Imported model: ${model.name}, supports image: ${model.llmSupportImage}")
          }
          imported
        } else {
          listOf()
        }
      }
    }

  Column(modifier = Modifier.padding(bottom = 8.dp)) {
    // Title
    Row(
      modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        task.icon ?: ImageVector.vectorResource(task.iconVectorResourceId!!),
        tint = getTaskIconColor(task = task),
        modifier = Modifier.size(16.dp),
        contentDescription = "",
      )
      Text(
        "${task.type.label} models",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = getTaskIconColor(task = task),
      )
    }

    // Online models list
    for (model in onlineModels) {
      ModelPickerItem(
        model = model,
        selected = model.name == modelManagerUiState.selectedModel.name,
        onModelSelected = onModelSelected,
        modelManagerUiState = modelManagerUiState
      )
    }

    // Imported models section
    if (importedModels.isNotEmpty()) {
      // Title for imported models
      Text(
        "Imported models",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp),
      )

      // Imported models list
      for (model in importedModels) {
        ModelPickerItem(
          model = model,
          selected = model.name == modelManagerUiState.selectedModel.name,
          onModelSelected = onModelSelected,
          modelManagerUiState = modelManagerUiState
        )
      }
    }
  }
}

@Composable
private fun ModelPickerItem(
  model: Model,
  selected: Boolean,
  onModelSelected: (Model) -> Unit,
  modelManagerUiState: ModelManagerUiState
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier =
      Modifier.fillMaxWidth()
        .clickable { onModelSelected(model) }
        .background(
          if (selected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent
        )
        .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Spacer(modifier = Modifier.width(24.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(model.name, style = MaterialTheme.typography.bodyMedium)
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        StatusIcon(downloadStatus = modelManagerUiState.modelDownloadStatus[model.name])
        Text(
          model.sizeInBytes.humanReadableSize(),
          color = MaterialTheme.colorScheme.secondary,
          style = labelSmallNarrow.copy(lineHeight = 10.sp),
        )
      }
    }
    if (selected) {
      Icon(Icons.Filled.CheckCircle, modifier = Modifier.size(16.dp), contentDescription = "")
    }
  }
}

// @Preview(showBackground = true)
// @Composable
// fun ModelPickerPreview() {
//   val context = LocalContext.current

//   GalleryTheme {
//     ModelPicker(
//       task = TASK_TEST1,
//       modelManagerViewModel = PreviewModelManagerViewModel(context = context),
//       onModelSelected = {},
//     )
//   }
// }
