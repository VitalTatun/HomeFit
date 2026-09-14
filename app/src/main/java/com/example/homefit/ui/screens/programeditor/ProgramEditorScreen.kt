package com.example.homefit.ui.screens.programeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homefit.model.Exercise

/**
 * Program Editor screen (create + edit).
 *
 * Renders [ProgramEditorUiState] owned by [ProgramEditorViewModel].
 * Navigation stays outside: [onCancel] pops back without saving (also wired
 * to the AppBar Back), [onSaved] pops back after a successful save
 * (signalled via `navigateBack`). Save itself lives in the destination-aware
 * AppBar and calls `ViewModel.save()`; this screen only shows `saveError`.
 * Reorder and new-exercise creation are out of scope: rows can only be
 * added from the existing catalog, removed, and edited in place.
 */
@Composable
fun ProgramEditorScreen(
    viewModel: ProgramEditorViewModel,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigateBack.collect { onSaved() }
    }

    when (val current = state) {
        ProgramEditorUiState.Loading -> EditorLoading(modifier)
        ProgramEditorUiState.Missing -> EditorMissing(onCancel, modifier)
        is ProgramEditorUiState.Error -> EditorError(
            message = current.message,
            onRetry = viewModel::retry,
            onBack = onCancel,
            modifier = modifier,
        )
        is ProgramEditorUiState.Content -> EditorContent(
            state = current,
            onNameChange = viewModel::onNameChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onRowSetsChange = viewModel::onRowSetsChange,
            onRowRepsChange = viewModel::onRowRepsChange,
            onRowWeightChange = viewModel::onRowWeightChange,
            onRemoveRow = viewModel::onRemoveRow,
            onAddExercise = viewModel::onAddExercise,
            modifier = modifier,
        )
    }
}

@Composable
private fun EditorLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EditorMissing(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Программа не найдена",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Назад")
        }
    }
}

@Composable
private fun EditorError(
    message: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Что-то пошло не так",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message ?: "Не удалось загрузить программу",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
        TextButton(onClick = onBack) {
            Text("Назад")
        }
    }
}

@Composable
private fun EditorContent(
    state: ProgramEditorUiState.Content,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRowSetsChange: (key: String, value: String) -> Unit,
    onRowRepsChange: (key: String, value: String) -> Unit,
    onRowWeightChange: (key: String, value: String) -> Unit,
    onRemoveRow: (key: String) -> Unit,
    onAddExercise: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text("Название") },
            singleLine = true,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.description,
            onValueChange = onDescriptionChange,
            label = { Text("Описание (необязательно)") },
            singleLine = true,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        text = "Упражнений пока нет. Пустую программу можно сохранить, " +
                            "но запустить её не получится, пока нет упражнений.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.rows, key = { it.key }) { row ->
                DraftRowCard(
                    row = row,
                    enabled = !state.isSaving,
                    onRowSetsChange = onRowSetsChange,
                    onRowRepsChange = onRowRepsChange,
                    onRowWeightChange = onRowWeightChange,
                    onRemoveRow = onRemoveRow,
                )
            }
            item {
                AddExerciseSection(
                    catalog = state.catalog,
                    enabled = !state.isSaving,
                    onAddExercise = onAddExercise,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (state.saveError != null) {
            Text(
                text = state.saveError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DraftRowCard(
    row: DraftRow,
    enabled: Boolean,
    onRowSetsChange: (key: String, value: String) -> Unit,
    onRowRepsChange: (key: String, value: String) -> Unit,
    onRowWeightChange: (key: String, value: String) -> Unit,
    onRemoveRow: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onRemoveRow(row.key) },
                    enabled = enabled,
                ) {
                    Text("Удалить")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = row.setsText,
                    onValueChange = { onRowSetsChange(row.key, it) },
                    label = { Text("Подходы") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.repsText,
                    onValueChange = { onRowRepsChange(row.key, it) },
                    label = { Text("Повт.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.weightText,
                    onValueChange = { onRowWeightChange(row.key, it) },
                    label = { Text("Вес, кг") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddExerciseSection(
    catalog: List<Exercise>,
    enabled: Boolean,
    onAddExercise: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Добавить упражнение",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (catalog.isEmpty()) {
            Text(
                text = "Каталог упражнений пуст",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            catalog.forEach { exercise ->
                TextButton(
                    onClick = { onAddExercise(exercise.id) },
                    enabled = enabled,
                ) {
                    Text("+ ${exercise.name}")
                }
            }
        }
    }
}
