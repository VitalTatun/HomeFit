package com.example.homefit.ui.screens.workout

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homefit.model.WorkoutSession
import com.example.homefit.model.WorkoutSet

/**
 * P2.5 Active Workout screen.
 *
 * Renders [WorkoutUiState] owned by [WorkoutViewModel]. Navigation stays
 * outside: [onFinished] pops back to Home after a successful finish,
 * [onBack] pops back without finishing (the session stays active).
 */
@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isFinishing by viewModel.isFinishing.collectAsStateWithLifecycle()
    val recordingExercises by viewModel.recordingExercises.collectAsStateWithLifecycle()
    val deletingSets by viewModel.deletingSets.collectAsStateWithLifecycle()
    val recordErrors by viewModel.recordErrors.collectAsStateWithLifecycle()
    val finishError by viewModel.finishError.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigateHome.collect { onFinished() }
    }

    when (val current = state) {
        WorkoutUiState.Loading -> WorkoutLoading(modifier)
        WorkoutUiState.Missing -> WorkoutMissing(onBack, modifier)
        is WorkoutUiState.Error -> WorkoutError(
            message = current.message,
            onRetry = viewModel::retry,
            onBack = onBack,
            modifier = modifier,
        )
        is WorkoutUiState.Active -> WorkoutContent(
            session = current.session,
            exercises = current.exercises,
            readOnly = false,
            isFinishing = isFinishing,
            recordingExercises = recordingExercises,
            deletingSets = deletingSets,
            recordErrors = recordErrors,
            finishError = finishError,
            onAddSet = viewModel::recordSet,
            onDeleteSet = viewModel::deleteSet,
            onConsumeRecordError = viewModel::consumeRecordError,
            onFinish = viewModel::finishWorkout,
            modifier = modifier,
        )
        is WorkoutUiState.Finished -> WorkoutContent(
            session = current.session,
            exercises = current.exercises,
            readOnly = true,
            isFinishing = false,
            recordingExercises = emptySet(),
            deletingSets = emptySet(),
            recordErrors = emptyMap(),
            finishError = null,
            onAddSet = { _, _, _ -> },
            onDeleteSet = {},
            onConsumeRecordError = {},
            onFinish = onFinished,
            finishedButtonLabel = "На главную",
            modifier = modifier,
        )
    }
}

@Composable
private fun WorkoutLoading(modifier: Modifier = Modifier) {
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
private fun WorkoutMissing(
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
            text = "Тренировка не найдена",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("На главную")
        }
    }
}

@Composable
private fun WorkoutError(
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
            text = message ?: "Не удалось загрузить тренировку",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
        TextButton(onClick = onBack) {
            Text("На главную")
        }
    }
}

@Composable
private fun WorkoutContent(
    session: WorkoutSession,
    exercises: List<ExerciseWithSets>,
    readOnly: Boolean,
    isFinishing: Boolean,
    recordingExercises: Set<String>,
    deletingSets: Set<String>,
    recordErrors: Map<String, String?>,
    finishError: String?,
    onAddSet: (sessionExerciseId: String, actualReps: Int, actualWeight: Double?) -> Unit,
    onDeleteSet: (setId: String) -> Unit,
    onConsumeRecordError: (sessionExerciseId: String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    finishedButtonLabel: String = "Завершить тренировку",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = exercises,
                key = { it.item.id },
            ) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    readOnly = readOnly,
                    isRecording = recordingExercises.contains(exercise.item.id),
                    deletingSets = deletingSets,
                    recordError = recordErrors[exercise.item.id],
                    onAddSet = onAddSet,
                    onDeleteSet = onDeleteSet,
                    onConsumeRecordError = onConsumeRecordError,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (finishError != null) {
            Text(
                text = finishError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Button(
            onClick = onFinish,
            enabled = !isFinishing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (isFinishing) {
                    "Завершение..."
                } else {
                    finishedButtonLabel
                },
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseWithSets,
    readOnly: Boolean,
    isRecording: Boolean,
    deletingSets: Set<String>,
    recordError: String?,
    onAddSet: (sessionExerciseId: String, actualReps: Int, actualWeight: Double?) -> Unit,
    onDeleteSet: (setId: String) -> Unit,
    onConsumeRecordError: (sessionExerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = exercise.item
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.exerciseName,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTarget(item.targetSets, item.targetReps, item.targetWeight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (exercise.sets.isEmpty()) {
                Text(
                    text = "Подходов пока нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                exercise.sets.sortedBy { it.setIndex }.forEach { set ->
                    SetRow(
                        set = set,
                        showDelete = !readOnly,
                        isDeleting = deletingSets.contains(set.id),
                        onDeleteSet = onDeleteSet,
                    )
                }
            }
            if (!readOnly) {
                Spacer(modifier = Modifier.height(8.dp))
                SetInput(
                    targetReps = item.targetReps,
                    targetWeight = item.targetWeight,
                    isRecording = isRecording,
                    recordError = recordError,
                    onAddSet = { reps, weight ->
                        onAddSet(item.id, reps, weight)
                    },
                    onConsumeError = { onConsumeRecordError(item.id) },
                )
            }
        }
    }
}

@Composable
private fun SetRow(
    set: WorkoutSet,
    showDelete: Boolean,
    isDeleting: Boolean,
    onDeleteSet: (setId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatSet(set),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (showDelete) {
            TextButton(
                onClick = { onDeleteSet(set.id) },
                enabled = !isDeleting,
            ) {
                Text("Удалить")
            }
        }
    }
}

@Composable
private fun SetInput(
    targetReps: Int,
    targetWeight: Double?,
    isRecording: Boolean,
    recordError: String?,
    onAddSet: (actualReps: Int, actualWeight: Double?) -> Unit,
    onConsumeError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var repsText by rememberSaveable(targetReps) { mutableStateOf(targetReps.toString()) }
    var weightText by rememberSaveable(targetWeight) {
        mutableStateOf(targetWeight?.let(::formatWeight) ?: "")
    }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = repsText,
                onValueChange = {
                    repsText = it
                    localError = null
                },
                label = { Text("Повт.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !isRecording,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    localError = null
                },
                label = { Text("Вес, кг") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !isRecording,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val reps = repsText.trim().toIntOrNull()
                    if (reps == null || reps < 0) {
                        localError = "Укажите повторы: целое число ≥ 0"
                        return@Button
                    }
                    val weight: Double? = if (weightText.isBlank()) {
                        null
                    } else {
                        weightText.trim().replace(',', '.').toDoubleOrNull().let {
                            if (it == null || it <= 0) {
                                localError = "Укажите вес > 0 или оставьте поле пустым"
                                return@Button
                            }
                            it
                        }
                    }
                    localError = null
                    onConsumeError()
                    onAddSet(reps, weight)
                },
                enabled = !isRecording,
            ) {
                Text(if (isRecording) "..." else "Добавить")
            }
        }
        val errorToShow = localError ?: recordError
        if (errorToShow != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorToShow,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun formatTarget(
    targetSets: Int,
    targetReps: Int,
    targetWeight: Double?,
): String {
    val base = "Цель: $targetSets × $targetReps"
    return if (targetWeight != null) {
        "$base · ${formatWeight(targetWeight)} кг"
    } else {
        base
    }
}

private fun formatSet(set: WorkoutSet): String {
    val weightPart = set.actualWeight?.let { " · ${formatWeight(it)} кг" }.orEmpty()
    return "Подход ${set.setIndex + 1}: ${set.actualReps} повт.$weightPart"
}

private fun formatWeight(weight: Double): String {
    val asLong = weight.toLong()
    return if (weight == asLong.toDouble()) {
        asLong.toString()
    } else {
        weight.toString()
    }
}
