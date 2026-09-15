package com.example.homefit.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.homefit.ui.screens.history.HistoryScreen
import com.example.homefit.ui.screens.history.HistoryViewModel
import com.example.homefit.ui.screens.history.HistoryViewModelFactory
import com.example.homefit.ui.screens.statistics.StatisticsScreen
import com.example.homefit.ui.screens.statistics.StatisticsViewModel
import com.example.homefit.ui.screens.statistics.StatisticsViewModelFactory
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
data class WorkoutTitleState(
    val key: Workout,
    val title: String,
)

/**
 * Top-level tabs of the [NavigationBar].
 *
 * Each tab owns its back stack ([Home] also owns the workout flow,
 * [History] owns read-only `Workout` details, [Statistics] is a root).
 */
private enum class TopTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        label = "Главная",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    HISTORY(
        label = "История",
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
    ),
    STATISTICS(
        label = "Статистика",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFitNavDisplay(
    workoutRepository: WorkoutRepository,
    modifier: Modifier = Modifier
) {
    val homeStack = rememberNavBackStack(Home)
    val historyStack = rememberNavBackStack(History)
    val statisticsStack = rememberNavBackStack(Statistics)
    var selectedTab by rememberSaveable { mutableStateOf(TopTab.HOME) }
    var editorBarState by remember { mutableStateOf<ProgramEditorBarState?>(null) }
    var workoutTitle by remember { mutableStateOf<WorkoutTitleState?>(null) }

    fun stackFor(tab: TopTab) = when (tab) {
        TopTab.HOME -> homeStack
        TopTab.HISTORY -> historyStack
        TopTab.STATISTICS -> statisticsStack
    }

    // Flattened stack rendered by NavDisplay: the Home root is always present
    // ("exit through home", multiple-backstacks recipe), the selected non-Home
    // tab is appended on top. Hidden tab stacks are retained as is.
    val flatBackStack = remember {
        mutableStateListOf<NavKey>().also { flat ->
            flat.addAll(homeStack)
            if (selectedTab != TopTab.HOME) flat.addAll(stackFor(selectedTab))
        }
    }

    fun syncFlat() {
        val expected = buildList {
            addAll(homeStack)
            if (selectedTab != TopTab.HOME) addAll(stackFor(selectedTab))
        }
        if (flatBackStack != expected) {
            flatBackStack.clear()
            flatBackStack.addAll(expected)
        }
    }

    fun selectTab(tab: TopTab) {
        if (tab == selectedTab) {
            // Reselect: pop the tab back to its root.
            val stack = stackFor(tab)
            while (stack.size > 1) stack.removeLastOrNull()
        } else {
            selectedTab = tab
        }
        syncFlat()
    }

    fun handleBack() {
        val stack = stackFor(selectedTab)
        if (stack.size > 1) {
            stack.removeLastOrNull()
        } else if (selectedTab != TopTab.HOME) {
            // Back on a non-Home root switches to Home instantly (the keyed
            // NavDisplay below is recreated, so no transition runs at all).
            selectedTab = TopTab.HOME
        } else {
            // Home root: legacy behavior (the system handles the rest).
            stack.removeLastOrNull()
        }
        syncFlat()
    }

    // Safety net keeping the flattened stack in sync (syncFlat is
    // idempotent, so this never loops).
    LaunchedEffect(
        selectedTab,
        homeStack.size,
        historyStack.size,
        statisticsStack.size,
    ) {
        syncFlat()
    }

    Scaffold(
        modifier = modifier,
        // Only the system navigation inset reaches the outer content: the top
        // stays full-bleed for the per-entry TopAppBar, the bottom reserves
        // exactly the NavigationBar height (the bar draws over the nav inset
        // itself, per M3). Inner entry Scaffolds exclude navigationBars, so
        // the bottom inset is counted exactly once.
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            NavigationBar {
                TopTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { outerPadding ->
        // Keyed by tab: switching tabs recreates NavDisplay, so the switch is
        // instant with no transition at all. Push/pop inside a tab keep the
        // same key and animate with the specs above; entry decorators are
        // remembered outside and survive the recreation.
        key(selectedTab) {
            NavDisplay(
                backStack = flatBackStack,
            modifier = Modifier.padding(outerPadding),
            onBack = { handleBack() },
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
            entry<Home> { key ->
                HomeFitScreen(
                    key = key,
                    onBack = { handleBack() },
                    editorBarState = editorBarState,
                    workoutTitle = workoutTitle,
                ) { innerPadding ->
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
                            homeStack.add(ProgramSelection)
                            syncFlat()
                        },
                        onResumeWorkout = dropUnlessResumed {
                            val sessionId = activeSession?.id ?: return@dropUnlessResumed
                            if (isResumingWorkout) return@dropUnlessResumed
                            // Dedupe: the top entry is already this workout.
                            if (homeStack.lastOrNull() == Workout(sessionId)) return@dropUnlessResumed
                            isResumingWorkout = true
                            try {
                                // Resume reopens the existing session id.
                                // It never calls startWorkout(), so no new session is created.
                                homeStack.add(Workout(sessionId))
                                syncFlat()
                            } finally {
                                isResumingWorkout = false
                            }
                        },
                        contentPadding = innerPadding
                    )
                }
            }
            entry<History> { key ->
                HomeFitScreen(
                    key = key,
                    onBack = { handleBack() },
                    editorBarState = editorBarState,
                    workoutTitle = workoutTitle,
                ) { innerPadding ->
                    val historyViewModel: HistoryViewModel = viewModel(
                        factory = HistoryViewModelFactory(
                            repository = workoutRepository,
                        ),
                    )
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onSessionClick = { sessionId ->
                            val key = Workout(sessionId)
                            if (historyStack.lastOrNull() != key) {
                                historyStack.add(key)
                                syncFlat()
                            }
                        },
                        contentPadding = innerPadding
                    )
                }
            }
            entry<Statistics> { key ->
                HomeFitScreen(
                    key = key,
                    onBack = { handleBack() },
                    editorBarState = editorBarState,
                    workoutTitle = workoutTitle,
                ) { innerPadding ->
                    val statisticsViewModel: StatisticsViewModel = viewModel(
                        factory = StatisticsViewModelFactory(
                            repository = workoutRepository,
                        ),
                    )
                    StatisticsScreen(
                        viewModel = statisticsViewModel,
                        contentPadding = innerPadding
                    )
                }
            }
            entry<ProgramSelection> { key ->
                HomeFitScreen(
                    key = key,
                    onBack = { handleBack() },
                    editorBarState = editorBarState,
                    workoutTitle = workoutTitle,
                ) { innerPadding ->
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
                                    homeStack.add(Workout(sessionId))
                                    syncFlat()
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
                            homeStack.add(ProgramEditor(programId = null))
                            syncFlat()
                        },
                        onEditProgram = { programId: String ->
                            val key = ProgramEditor(programId = programId)
                            // Dedupe: the top entry is already this editor.
                            if (homeStack.lastOrNull() != key) {
                                homeStack.add(key)
                                syncFlat()
                            }
                        },
                        contentPadding = innerPadding
                    )
                }
            }
            entry<ProgramEditor> { key ->
                HomeFitScreen(
                    key = key,
                    onBack = { handleBack() },
                    editorBarState = editorBarState,
                    workoutTitle = workoutTitle,
                ) { innerPadding ->
                    val programEditorViewModel: ProgramEditorViewModel = viewModel(
                        factory = ProgramEditorViewModelFactory(
                            programId = key.programId,
                            repository = workoutRepository,
                        ),
                    )
                    val editorState by programEditorViewModel.uiState.collectAsStateWithLifecycle()
                    val editorContent = editorState as? ProgramEditorUiState.Content
                    val isEditorTop = flatBackStack.lastOrNull() == key
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
                        onCancel = { handleBack() },
                        onSaved = { handleBack() },
                        contentPadding = innerPadding
                    )
                }
            }
            entry<Workout> { key ->
                HomeFitScreen(
                    key = key,
                    onBack = { handleBack() },
                    editorBarState = editorBarState,
                    workoutTitle = workoutTitle,
                ) { innerPadding ->
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
                    val isWorkoutTop = flatBackStack.lastOrNull() == key
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
                        onFinished = { handleBack() },
                        onBack = { handleBack() },
                        contentPadding = innerPadding
                    )
                }
            }
        }
        )
    }
}
}

/**
 * Common destination wrapper that provides a Scaffold and TopBar for each screen.
 * This ensures the AppBar is part of the destination and participates in transitions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFitScreen(
    key: NavKey,
    onBack: () -> Unit,
    editorBarState: ProgramEditorBarState?,
    workoutTitle: WorkoutTitleState?,
    modifier: Modifier = Modifier,
    content: @Composable (innerPadding: PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        // Exclude the system navigation inset here: the outer Scaffold around
        // NavDisplay already reserves the NavigationBar height (which covers
        // that inset). Top, start and end behave exactly as before.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
            HomeFitTopBar(
                current = key,
                editorBarState = editorBarState,
                workoutTitle = workoutTitle,
                onBack = onBack,
            )
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

/**
 * Destination-aware AppBar driven by the visible top entry.
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
            },
        )
        is Workout -> TopAppBar(
            modifier = modifier,
            title = {
                val titleText = workoutTitle?.takeIf { it.key == current }?.title ?: ""
                if (titleText.isNotEmpty()) {
                    Text(titleText)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
            },
        )
        is History -> TopAppBar(
            // Top-level tab root: no Back arrow (reached via NavigationBar).
            modifier = modifier,
            title = { Text("История") },
        )
        is Statistics -> TopAppBar(
            // Top-level tab root: no Back arrow (reached via NavigationBar).
            modifier = modifier,
            title = { Text("Статистика") },
        )
        else -> TopAppBar(
            modifier = modifier,
            title = { Text("HomeFit") },
        )
    }
}
