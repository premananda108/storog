package ua.pp.prema.storog

import ua.pp.prema.storog.engine.DownloadException
import ua.pp.prema.storog.engine.PreflightChecker
import ua.pp.prema.storog.engine.PreflightResult

sealed class ModelStatus {
    /** Model not selected / not loaded. */
    object NotLoaded : ModelStatus()

    /** Downloading model file. */
    data class Downloading(val modelName: String, val percent: Int) : ModelStatus()

    /** File downloaded, initializing engine. */
    data class Loading(val modelName: String) : ModelStatus()

    /** Engine ready for work. */
    data class Ready(val modelName: String, val backend: String) : ModelStatus()

    /** Error (fatal or retriable). */
    data class Error(val message: String, val canRetry: Boolean = true) : ModelStatus()
}

sealed class UiEvent {
    data class ShowError(val message: String) : UiEvent()
    data class ShowPreflight(val result: PreflightResult) : UiEvent()
    data class ShowGpuFallback(val reason: String) : UiEvent()
    object ShowModelSelection : UiEvent()
}

data class StorogUiState(
    val modelStatus: ModelStatus = ModelStatus.NotLoaded,
    val isMonitoringEnabled: Boolean = false
) {
    val statusText: String get() = when (modelStatus) {
        is ModelStatus.NotLoaded          -> "Model not loaded"
        is ModelStatus.Downloading        -> "Downloading: ${modelStatus.percent}%"
        is ModelStatus.Loading            -> "Loading ${modelStatus.modelName}…"
        is ModelStatus.Ready              -> "${modelStatus.modelName} [${modelStatus.backend}]"
        is ModelStatus.Error              -> "❌ Error"
    }
}
