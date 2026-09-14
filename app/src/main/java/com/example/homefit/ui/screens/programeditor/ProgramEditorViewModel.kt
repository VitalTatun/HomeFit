package com.example.homefit.ui.screens.programeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.homefit.data.ProgramItemInput
import com.example.homefit.data.WorkoutRepository
import com.example.homefit.model.Exercise
import com.example.homefit.model.WorkoutProgram
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owner of the Program Editor screen state.
 *
 * Single source of [ProgramEditorUiState]. `programId == null` is create
 * mode (empty draft from the start); otherwise the draft is initialized
 * once from [WorkoutRepository.observeProgramDetail] and never overwritten
 * by later emissions, so typing is never clobbered by database updates.
 * A later `null` emission flips to [ProgramEditorUiState.Missing].
 *
 * Owns all coroutine work ([viewModelScope]) and the save guard, so fast
 * double-taps cannot create duplicate programs or rows. Navigation itself
 * stays in `HomeFitNavDisplay`: completion is signalled via [navigateBack].
 */
class ProgramEditorViewModel(
    private val programId: String?,
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    private val _description = MutableStateFlow("")
    private val _rows = MutableStateFlow<List<DraftRow>>(emptyList())
    private val _initialized = MutableStateFlow(programId == null)
    private val _missing = MutableStateFlow(false)
    private val _loadError = MutableStateFlow<String?>(null)
    private val _isSaving = MutableStateFlow(false)
    private val _saveError = MutableStateFlow<String?>(null)

    private val catalog: StateFlow<List<Exercise>> =
        repository.observeExercises()
            .catch { emit(emptyList()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val uiState: StateFlow<ProgramEditorUiState> =
        combine(
            _initialized,
            _missing,
            _loadError,
            _name,
            _description,
            _rows,
            catalog,
            _isSaving,
            _saveError,
        ) { args ->
            val initialized = args[0] as Boolean
            val missing = args[1] as Boolean
            val loadError = args[2] as String?
            val name = args[3] as String
            val description = args[4] as String
            @Suppress("UNCHECKED_CAST")
            val rows = args[5] as List<DraftRow>
            @Suppress("UNCHECKED_CAST")
            val exercises = args[6] as List<Exercise>
            val isSaving = args[7] as Boolean
            val saveError = args[8] as String?
            when {
                loadError != null -> ProgramEditorUiState.Error(loadError)
                missing -> ProgramEditorUiState.Missing
                !initialized -> ProgramEditorUiState.Loading
                else -> ProgramEditorUiState.Content(
                    programId = programId,
                    name = name,
                    description = description,
                    rows = rows,
                    catalog = exercises,
                    isSaving = isSaving,
                    saveError = saveError,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgramEditorUiState.Loading,
        )

    private val navigateBackChannel = Channel<Unit>(Channel.BUFFERED)

    /**
     * One-shot signal emitted after a successful save.
     * Collected by the screen to pop back.
     */
    val navigateBack = navigateBackChannel.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        if (programId != null) {
            load()
        }
    }

    /** Re-subscribes to the program detail flow after a [ProgramEditorUiState.Error]. */
    fun retry() {
        _loadError.value = null
        if (programId != null) {
            load()
        }
    }

    private fun load() {
        val id = programId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.observeProgramDetail(id)
                .catch { e ->
                    _loadError.value = e.message ?: "Не удалось загрузить программу"
                }
                .collect { detail ->
                    if (detail == null) {
                        _missing.value = true
                    } else if (!_initialized.value) {
                        _name.value = detail.program.name
                        _description.value = detail.program.description.orEmpty()
                        _rows.value = detail.items.map { item ->
                            DraftRow(
                                key = item.id,
                                itemId = item.id,
                                exerciseId = item.exerciseId,
                                exerciseName = detail.exercisesById[item.exerciseId]?.name
                                    ?: item.exerciseId,
                                setsText = item.targetSets.toString(),
                                repsText = item.targetReps.toString(),
                                weightText = item.targetWeight?.let(::formatWeight).orEmpty(),
                            )
                        }
                        _missing.value = false
                        _initialized.value = true
                    } else {
                        _missing.value = false
                        val names = detail.exercisesById.mapValues { it.value.name }
                        _rows.value = _rows.value.map { row ->
                            row.copy(exerciseName = names[row.exerciseId] ?: row.exerciseName)
                        }
                    }
                }
        }
    }

    fun onNameChange(value: String) {
        _name.value = value
        _saveError.value = null
    }

    fun onDescriptionChange(value: String) {
        _description.value = value
        _saveError.value = null
    }

    fun onRowSetsChange(key: String, value: String) {
        updateRow(key) { it.copy(setsText = value) }
    }

    fun onRowRepsChange(key: String, value: String) {
        updateRow(key) { it.copy(repsText = value) }
    }

    fun onRowWeightChange(key: String, value: String) {
        updateRow(key) { it.copy(weightText = value) }
    }

    private inline fun updateRow(key: String, update: (DraftRow) -> DraftRow) {
        _rows.value = _rows.value.map { row ->
            if (row.key == key) update(row) else row
        }
        _saveError.value = null
    }

    fun onRemoveRow(key: String) {
        _rows.value = _rows.value.filter { it.key != key }
        _saveError.value = null
    }

    fun onAddExercise(exerciseId: String) {
        val name = catalog.value.firstOrNull { it.id == exerciseId }?.name ?: exerciseId
        val row = DraftRow(
            key = "new-${UUID.randomUUID()}",
            itemId = null,
            exerciseId = exerciseId,
            exerciseName = name,
            setsText = DEFAULT_SETS,
            repsText = DEFAULT_REPS,
            weightText = "",
        )
        _rows.value = _rows.value + row
        _saveError.value = null
    }

    /**
     * Validates the draft and saves it via the repository.
     *
     * Guarded against double-tap: a second call while one is in flight is
     * ignored. Empty programs are allowed (starting them is rejected later
     * by `startWorkout`, not here).
     */
    fun save() {
        if (_isSaving.value) return
        val name = _name.value.trim()
        if (name.isBlank()) {
            _saveError.value = "Укажите название программы"
            return
        }
        val inputs = mutableListOf<ProgramItemInput>()
        for (row in _rows.value) {
            val sets = row.setsText.trim().toIntOrNull()
            if (sets == null || sets < 1) {
                _saveError.value = "«${row.exerciseName}»: подходы — целое число ≥ 1"
                return
            }
            val reps = row.repsText.trim().toIntOrNull()
            if (reps == null || reps < 1) {
                _saveError.value = "«${row.exerciseName}»: повторы — целое число ≥ 1"
                return
            }
            val weight: Double? = if (row.weightText.isBlank()) {
                null
            } else {
                val parsed = row.weightText.trim().replace(',', '.').toDoubleOrNull()
                if (parsed == null || parsed <= 0) {
                    _saveError.value = "«${row.exerciseName}»: вес > 0 или пустое поле"
                    return
                }
                parsed
            }
            inputs.add(
                ProgramItemInput(
                    id = row.itemId,
                    exerciseId = row.exerciseId,
                    targetSets = sets,
                    targetReps = reps,
                    targetWeight = weight,
                ),
            )
        }
        viewModelScope.launch {
            _isSaving.value = true
            _saveError.value = null
            try {
                if (programId == null) {
                    repository.createProgram(
                        name = name,
                        description = _description.value.trim().ifEmpty { null },
                        items = inputs,
                    )
                } else {
                    repository.saveProgram(
                        program = WorkoutProgram(
                            id = programId,
                            name = name,
                            description = _description.value.trim().ifEmpty { null },
                        ),
                        items = inputs,
                    )
                }
                navigateBackChannel.send(Unit)
            } catch (e: Exception) {
                _saveError.value = e.message ?: "Не удалось сохранить программу"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private companion object {
        const val DEFAULT_SETS = "3"
        const val DEFAULT_REPS = "10"
    }
}

private fun formatWeight(weight: Double): String {
    val asLong = weight.toLong()
    return if (weight == asLong.toDouble()) {
        asLong.toString()
    } else {
        weight.toString()
    }
}

/**
 * Minimal factory: passes the [ProgramEditor] NavKey argument and the
 * repository from the manual composition root into [ProgramEditorViewModel].
 * No DI framework is used.
 */
class ProgramEditorViewModelFactory(
    private val programId: String?,
    private val repository: WorkoutRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProgramEditorViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return ProgramEditorViewModel(programId, repository) as T
    }
}
