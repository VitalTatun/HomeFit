package com.example.homefit.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.homefit.data.WorkoutRepository
import com.example.homefit.ui.screens.home.HomeScreen
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
                val scope = rememberCoroutineScope()
                var isStartingWorkout by remember { mutableStateOf(false) }
                var startErrorMessage by remember { mutableStateOf<String?>(null) }
                HomeScreen(
                    onStartWorkout = dropUnlessResumed {
                        if (isStartingWorkout) return@dropUnlessResumed
                        isStartingWorkout = true
                        startErrorMessage = null
                        scope.launch {
                            try {
                                val programId = workoutRepository.ensureDefaultProgram()
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
                    startErrorMessage = startErrorMessage
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
