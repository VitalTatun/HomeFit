package com.example.homefit.ui.screens.programselection

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homefit.model.WorkoutProgram

/**
 * P2.6 Program Selection screen.
 *
 * Renders [ProgramSelectionUiState] owned by [ProgramSelectionViewModel].
 * Navigation stays outside: [onProgramSelected] only reports the chosen
 * program id, `HomeFitNavDisplay` performs `startWorkout` and the back stack
 * update (including the double-tap guard and the "already active" message
 * surfaced via [startErrorMessage]). [onCreateProgram] / [onEditProgram]
 * only request navigation to the Program Editor.
 */
@Composable
fun ProgramSelectionScreen(
    viewModel: ProgramSelectionViewModel,
    onProgramSelected: (String) -> Unit,
    onCreateProgram: () -> Unit,
    onEditProgram: (String) -> Unit,
    modifier: Modifier = Modifier,
    startErrorMessage: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Button(
            onClick = onCreateProgram,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Создать программу")
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (startErrorMessage != null) {
            Text(
                text = startErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        when (val current = state) {
            ProgramSelectionUiState.Loading -> SelectionLoading(Modifier.weight(1f))
            ProgramSelectionUiState.Empty -> SelectionEmpty(
                onCreateProgram = onCreateProgram,
                modifier = Modifier.weight(1f),
            )
            is ProgramSelectionUiState.Error -> SelectionError(
                message = current.message,
                onRetry = viewModel::retry,
                modifier = Modifier.weight(1f),
            )
            is ProgramSelectionUiState.Content -> ProgramList(
                programs = current.programs,
                onProgramSelected = onProgramSelected,
                onEditProgram = onEditProgram,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectionLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SelectionEmpty(
    onCreateProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Программ пока нет",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onCreateProgram) {
            Text("Создать программу")
        }
    }
}

@Composable
private fun SelectionError(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message ?: "Не удалось загрузить программы",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun ProgramList(
    programs: List<WorkoutProgram>,
    onProgramSelected: (String) -> Unit,
    onEditProgram: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(programs, key = { it.id }) { program ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onProgramSelected(program.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(program.name)
                }
                TextButton(onClick = { onEditProgram(program.id) }) {
                    Text("Ред.")
                }
            }
        }
    }
}
