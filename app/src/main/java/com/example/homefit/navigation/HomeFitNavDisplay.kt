package com.example.homefit.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.homefit.data.WorkoutRepository
import com.example.homefit.ui.screens.home.HomeScreen
import com.example.homefit.ui.screens.home.HomeUiState
import com.example.homefit.ui.screens.home.HomeViewModel
import com.example.homefit.ui.screens.home.HomeViewModelFactory
import com.example.homefit.ui.screens.programselection.ProgramSelectionScreen
import com.example.homefit.ui.screens.programselection.ProgramSelectionViewModel
import com.example.homefit.ui.screens.programselection.ProgramSelectionViewModelFactory
import com.example.homefit.ui.screens.workout.WorkoutScreen
import com.example.homefit.ui.screens.workout.WorkoutViewModel
import com.example.homefit.ui.screens.workout.WorkoutViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun HomeFitNavDisplay(
    workoutRepository: WorkoutRepository,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(Home)

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Home> {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModelFactory(
                        repository = workoutRepository,
                    ),
                )
                val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                var isResumingWorkout by remember { mutableStateOf(false) }
                val activeSession = (homeState as? HomeUiState.Content)?.activeSession
                HomeScreen(
                    activeSession = activeSession,
                    onStartWorkout = dropUnlessResumed {
                        backStack.add(ProgramSelection)
                    },
                    onResumeWorkout = dropUnlessResumed {
                        val sessionId = activeSession?.id ?: return@dropUnlessResumed
                        if (isResumingWorkout) return@dropUnlessResumed
                        // Dedupe: the top entry is already this workout.
                        if (backStack.lastOrNull() == Workout(sessionId)) return@dropUnlessResumed
                        isResumingWorkout = true
                        try {
                            // Resume reopens the existing session id.
                            // It never calls startWorkout(), so no new session is created.
                            backStack.add(Workout(sessionId))
                        } finally {
                            isResumingWorkout = false
                        }
                    },
                )
            }
            entry<ProgramSelection> {
                val selectionViewModel: ProgramSelectionViewModel = viewModel(
                    factory = ProgramSelectionViewModelFactory(
                        repository = workoutRepository,
                    ),
                )
                val scope = rememberCoroutineScope()
                var isStartingWorkout by remember { mutableStateOf(false) }
                var startErrorMessage by remember { mutableStateOf<String?>(null) }
                ProgramSelectionScreen(
                    viewModel = selectionViewModel,
                    onProgramSelected = { programId ->
                        if (isStartingWorkout) return@ProgramSelectionScreen
                        isStartingWorkout = true
                        startErrorMessage = null
                        scope.launch {
                            try {
                                val sessionId = workoutRepository.startWorkout(programId)
                                backStack.add(Workout(sessionId))
                            } catch (e: IllegalStateException) {
                                // Expected conflict: an unfinished session already exists.
                                // Show it instead of crashing; anything else is rethrown
                                // so unexpected errors are never masked as this conflict.
                                if (e.message?.contains("already active") == true) {
                                    startErrorMessage = "Тренировка уже запущена"
                                } else {
                                    throw e
                                }
                            } finally {
                                isStartingWorkout = false
                            }
                        }
                    },
                    startErrorMessage = startErrorMessage,
                )
            }
            entry<Workout> { key ->
                val workoutViewModel: WorkoutViewModel = viewModel(
                    factory = WorkoutViewModelFactory(
                        sessionId = key.sessionId,
                        repository = workoutRepository,
                    ),
                )
                WorkoutScreen(
                    viewModel = workoutViewModel,
                    onFinished = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        }
    )
}
