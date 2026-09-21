package com.example.ui.screens.manra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.common.Result
import com.example.data.model.ManraChoice
import com.example.data.model.ManraDetail
import com.example.data.model.ManraLine
import com.example.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Halaman detail satu Manra: info, daftar chapter, dan karakter. */
class ManraDetailViewModel(
    private val repository: AnimeRepository,
    val manraId: String
) : ViewModel() {

    private val _state = MutableStateFlow<Result<ManraDetail>>(Result.Loading)
    val state: StateFlow<Result<ManraDetail>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = Result.Loading
        viewModelScope.launch {
            _state.value = repository.getManraDetail(manraId)
        }
    }
}

data class ManraReaderState(
    val lines: List<ManraLine> = emptyList(),
    val index: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Sedang memproses pilihan (simpan ke server + ambil lanjutan). */
    val isBusy: Boolean = false,
    val finished: Boolean = false
) {
    val current: ManraLine? get() = lines.getOrNull(index)
}

/**
 * Pembaca chapter. Baris adegan diambil sekali dari chapter/play. Saat
 * bertemu baris pilihan: pilihan disimpan ke server (chapter/choose) lalu
 * lanjutannya diambil dengan id_last_line. Kalau server menolak (mis. butuh
 * login Animein), cerita tetap jalan lurus ke baris berikutnya di daftar
 * yang sudah ada -- jadi percabangan cerita bisa tidak akurat.
 */
class ManraReaderViewModel(
    private val repository: AnimeRepository,
    private val manraId: String,
    private val chapterId: String
) : ViewModel() {

    private val _state = MutableStateFlow(ManraReaderState())
    val state: StateFlow<ManraReaderState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ManraReaderState(isLoading = true)
        viewModelScope.launch {
            when (val result = repository.getManraChapterLines(chapterId)) {
                is Result.Success -> _state.value = if (result.data.isEmpty()) {
                    ManraReaderState(isLoading = false, error = "Chapter ini belum punya isi.")
                } else {
                    ManraReaderState(lines = result.data, isLoading = false)
                }
                is Result.Error -> _state.value =
                    ManraReaderState(isLoading = false, error = result.message)
                is Result.Loading -> Unit
            }
        }
    }

    fun next() {
        val s = _state.value
        if (s.isLoading || s.isBusy || s.finished) return
        val line = s.current ?: return
        if (line.isChoice) return
        _state.update { cur ->
            if (cur.index < cur.lines.lastIndex) cur.copy(index = cur.index + 1) else cur.copy(finished = true)
        }
    }

    fun choose(choice: ManraChoice) {
        val s = _state.value
        val line = s.current ?: return
        if (s.isLoading || s.isBusy || !line.isChoice) return

        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            var continuation: List<ManraLine>? = null
            val saved = repository.chooseManraOption(manraId, chapterId, line.id, choice.id)
            if (saved is Result.Success) {
                val more = repository.getManraChapterLines(chapterId, lastLineId = line.id)
                if (more is Result.Success && more.data.isNotEmpty()) continuation = more.data
            }
            val newLines = continuation
            _state.update { cur ->
                when {
                    // Server memberi lanjutan sesuai pilihan: ganti sisa daftar.
                    newLines != null -> cur.copy(
                        lines = cur.lines.take(cur.index + 1) + newLines,
                        index = cur.index + 1,
                        isBusy = false
                    )
                    // Cadangan: lanjut ke baris berikutnya di daftar yang ada.
                    cur.index < cur.lines.lastIndex -> cur.copy(index = cur.index + 1, isBusy = false)
                    else -> cur.copy(isBusy = false, finished = true)
                }
            }
        }
    }
}
