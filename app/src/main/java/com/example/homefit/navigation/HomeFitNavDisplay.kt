package com.example.homefit.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.homefit.data.WorkoutRepository
import com.example.homefit.ui.screens.home.HomeScreen
import com.example.homefit.ui.screens.home.HomeUiState
import com.example.homefit.ui.screens.home.HomeViewModel
import com.example.homefit.ui.screens.home.HomeViewModelFactory
import com.example.homefit.ui.screens.programeditor.ProgramEditorScreen
import com.example.homefit.ui.screens.programeditor.ProgramEditorUiState
import com.example.homefit.ui.screens.programeditor.ProgramEditorViewModel
import com.example.homefit.ui.screens.programeditor.ProgramEditorViewModelFactory
import com.example.homefit.ui.screens.programselection.ProgramSelectionScreen
import com.example.homefit.ui.screens.programselection.ProgramSelectionViewModel
import com.example.homefit.ui.screens.programselection.ProgramSelectionViewModelFactory
import com.example.homefit.ui.screens.workout.WorkoutScreen
import com.example.homefit.ui.screens.workout.WorkoutUiState
import com.example.homefit.ui.screens.workout.WorkoutViewModel
import com.example.homefit.ui.screens.workout.WorkoutViewModelFactory
import kotlinx.coroutines.launch

/**
 * Save action state published by the top [ProgramEditor] entry for the AppBar.
 *
 * The editor [ProgramEditorViewModel] stays scoped to its NavEntry; only
 * [isSaving] and [onSave] ([ProgramEditorViewModel.save]) are hoisted so the
 * destination-aware AppBar can render Save without owning the ViewModel.
 */
private data class ProgramEditorBarState(
    val key: ProgramEditor,
    val isSaving: Boolean,
    val onSave: () -> Unit,
)

/** Program name published by the top [Workout] entry for the AppBar title. */
private data class WorkoutTitleState(
    val key: Workout,
    val title: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFitNavDisplay(
    workoutRepository: WorkoutRepository,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(Home)
    var editorBarState by remember { mutableStateOf<ProgramEditorBarState?>(null) }
    var workoutTitle by remember { mutableStateOf<WorkoutTitleState?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            HomeFitTopBar(
                current = backStack.lastOrNull(),
                editorBarState = editorBarState,
                workoutTitle = workoutTitle,
                onBack = { backStack.removeLastOrNull() },
            )
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { -it / 7 },
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300)))
            },
            popTransitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { -it / 7 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300)))
            },
            predictivePopTransitionSpec = {
                (slideInHorizontally(
                    initialOffsetX = { -it / 7 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300)))
            },
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
                    onCreateProgram = dropUnlessResumed {
                        backStack.add(ProgramEditor(programId = null))
                    },
                    onEditProgram = { programId: String ->
                        val key = ProgramEditor(programId = programId)
                        // Dedupe: the top entry is already this editor.
                        if (backStack.lastOrNull() != key) {
                            backStack.add(key)
                        }
                    },
                )
            }
            entry<ProgramEditor> { key ->
                val programEditorViewModel: ProgramEditorViewModel = viewModel(
                    factory = ProgramEditorViewModelFactory(
                        programId = key.programId,
                        repository = workoutRepository,
                    ),
                )
                val editorState by programEditorViewModel.uiState.collectAsStateWithLifecycle()
                val editorContent = editorState as? ProgramEditorUiState.Content
                val isEditorTop = backStack.lastOrNull() == key
                LaunchedEffect(key, isEditorTop, editorContent?.isSaving) {
                    editorBarState = if (isEditorTop && editorContent != null) {
                        ProgramEditorBarState(
                            key = key,
                            isSaving = editorContent.isSaving,
                            onSave = programEditorViewModel::save,
                        )
                    } else if (editorBarState?.key == key) {
                        null
                    } else {
                        editorBarState
                    }
                }
                DisposableEffect(key) {
                    onDispose {
                        if (editorBarState?.key == key) editorBarState = null
                    }
                }
                ProgramEditorScreen(
                    viewModel = programEditorViewModel,
                    onCancel = { backStack.removeLastOrNull() },
                    onSaved = { backStack.removeLastOrNull() },
                )
            }
            entry<Workout> { key ->
                val workoutViewModel: WorkoutViewModel = viewModel(
                    factory = WorkoutViewModelFactory(
                        sessionId = key.sessionId,
                        repository = workoutRepository,
                    ),
                )
                val workoutState by workoutViewModel.uiState.collectAsStateWithLifecycle()
                val programName = when (val current = workoutState) {
                    is WorkoutUiState.Active -> current.session.programName
                    is WorkoutUiState.Finished -> current.session.programName
                    else -> null
                }
                val isWorkoutTop = backStack.lastOrNull() == key
                LaunchedEffect(key, isWorkoutTop, programName) {
                    workoutTitle = if (isWorkoutTop && programName != null) {
                        WorkoutTitleState(key = key, title = programName)
                    } else if (workoutTitle?.key == key) {
                        null
                    } else {
                        workoutTitle
                    }
                }
                DisposableEffect(key) {
                    onDispose {
                        if (workoutTitle?.key == key) workoutTitle = null
                    }
                }
                WorkoutScreen(
                    viewModel = workoutViewModel,
                    onFinished = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        }
        )
    }
}

/**
 * Destination-aware AppBar driven by `backStack.lastOrNull()`.
 *
 * Home shows no Back; every other destination pops via the same
 * `removeLastOrNull()` used by the screens. The editor Save action calls
 * the entry-scoped [ProgramEditorViewModel.save] and reflects `isSaving`;
 * it is hidden while the editor is loading/missing/in error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFitTopBar(
    current: NavKey?,
    editorBarState: ProgramEditorBarState?,
    workoutTitle: WorkoutTitleState?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (current) {
        is ProgramEditor -> {
            val bar = editorBarState?.takeIf { it.key == current }
            TopAppBar(
                modifier = modifier,
                title = {
                    Text(
                        if (current.programId == null) "Новая программа"
                        else "Редактирование программы"
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    if (bar != null) {
                        TextButton(
                            onClick = bar.onSave,
                            enabled = !bar.isSaving,
                        ) {
                            Text(if (bar.isSaving) "Сохранение..." else "Сохранить")
                        }
                    }
                },
            )
        }
        is ProgramSelection -> TopAppBar(
            modifier = modifier,
            title = { Text("Выбор программы") },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("Назад") }
            },
        )
        is Workout -> TopAppBar(
            modifier = modifier,
            title = {
                Text(workoutTitle?.takeIf { it.key == current }?.title ?: "Тренировка")
            },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("Назад") }
            },
        )
        else -> TopAppBar(
            modifier = modifier,
            title = { Text("HomeFit") },
        )
    }
}
