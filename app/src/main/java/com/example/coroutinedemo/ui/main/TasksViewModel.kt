package com.example.coroutinedemo.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coroutinedemo.data.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class TasksViewModel(
    private val repository: TaskRepository = TaskRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadData(shouldFail: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                coroutineScope {
                    // Latihan 9 No. 1: 3 async jobs yang dieksekusi secara konkuren
                    val tasks = async { repository.getTasks(shouldFail) }
                    val announcement = async { repository.getAnnouncement() }
                    val courseCount = async { repository.getCourseCount() }

                    val loadedTasks = tasks.await()
                    val loadedAnnouncement = announcement.await()
                    val loadedCourseCount = courseCount.await()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            tasks = loadedTasks,
                            announcement = loadedAnnouncement,
                            courseCount = loadedCourseCount
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Latihan 9 No. 3: Rethrow CancellationException agar pembatalan berjalan lancar
                throw e
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load: ${e.message}"
                    )
                }
            }
        }
    }

    fun cancelLoading() {
        loadJob?.cancel()
        _uiState.update {
            it.copy(isLoading = false, errorMessage = "Loading cancelled")
        }
    }

    fun onTap() {
        _uiState.update { it.copy(tapCount = it.tapCount + 1) }
    }

    fun blockMainThread() {
        Thread.sleep(3000L)
    }

    /**
     * Latihan 9 No. 6: Tantangan - Sort tugas pada Dispatchers.Default
     */
    fun sortTasks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSorting = true) }
            val currentTasks = _uiState.value.tasks
            val currentOrder = _uiState.value.isSortedAscending
            val sortedList = withContext(Dispatchers.Default) {
                if (currentOrder) {
                    currentTasks.sortedByDescending { it.title }
                } else {
                    currentTasks.sortedBy { it.title }
                }
            }
            _uiState.update {
                it.copy(
                    tasks = sortedList,
                    isSorting = false,
                    isSortedAscending = !currentOrder
                )
            }
        }
    }

    /**
     * Modifikasi/Pengembangan: Mengubah kata kunci pencarian.
     */
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Modifikasi/Pengembangan: Mengubah status penyelesaian tugas.
     */
    fun toggleTaskCompletion(taskId: Int) {
        _uiState.update { state ->
            val updatedTasks = state.tasks.map { task ->
                if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
            }
            state.copy(tasks = updatedTasks)
        }
    }
}
