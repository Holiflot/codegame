package com.holiflot.astrominer

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.holiflot.astrominer.core.game.CompileResult
import com.holiflot.astrominer.core.game.Game
import com.holiflot.astrominer.core.game.Item
import com.holiflot.astrominer.core.game.RunListener
import com.holiflot.astrominer.core.game.WorldSnapshot
import com.holiflot.astrominer.core.lang.Feature
import com.holiflot.astrominer.core.lang.ScriptError
import com.holiflot.astrominer.core.progress.Progress
import com.holiflot.astrominer.core.progress.SaveData
import com.holiflot.astrominer.core.progress.UnlockDef
import com.holiflot.astrominer.core.progress.Unlocks
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SimSpeed(val label: String, val factor: Double) {
    X1("1×", 1.0),
    X4("4×", 4.0),
    X16("16×", 16.0),
    MAX("Maks", Double.POSITIVE_INFINITY),
}

data class ConsoleLine(val text: String, val isError: Boolean = false)

data class UnlockRow(
    val def: UnlockDef,
    val level: Int,
    val nextCost: Map<Item, Long>?,
    val missing: List<String>,
    val canBuy: Boolean,
)

data class GameUiState(
    val world: WorldSnapshot,
    val inventory: Map<Item, Long>,
    val running: Boolean,
    val stepping: Boolean,
    val currentLine: Int?,
    val errorLines: Set<Int>,
    val console: List<ConsoleLine>,
    val speed: SimSpeed,
    val unlocks: List<UnlockRow>,
    val unlockedIds: Set<String>,
    val features: Set<Feature>,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val saved = prefs.getString(KEY_SAVE, null)?.let { SaveData.fromJson(it) }
    private val game = Game(saved?.let { Progress.fromSave(it) } ?: Progress())

    var code by mutableStateOf(TextFieldValue(saved?.code?.takeIf { it.isNotBlank() } ?: DEFAULT_CODE))
        private set

    private var runJob: Job? = null
    private var running = false
    private var stepMode = false
    private val stepSignal = Channel<Unit>(Channel.CONFLATED)
    private var currentLine: Int? = null
    private var errorLines = emptySet<Int>()
    private val console = ArrayDeque<ConsoleLine>()
    private var speed = SimSpeed.X1

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    init {
        // Program çalışmıyorken de zaman akar; kayalar ve kristaller büyümeye devam eder.
        viewModelScope.launch {
            while (true) {
                delay(IDLE_FRAME_MS)
                if (!running) {
                    game.idle((IDLE_FRAME_MS / BASE_MS_PER_TICK).toLong())
                    publish()
                }
            }
        }
    }

    // ---- Editör ----

    fun onCodeChange(new: TextFieldValue) {
        if (running) return
        if (new.text != code.text) errorLines = emptySet()
        code = autoIndent(code, new)
    }

    fun insert(snippet: String) {
        if (running) return
        val sel = code.selection
        val start = minOf(sel.start, sel.end)
        val end = maxOf(sel.start, sel.end)
        code = TextFieldValue(code.text.replaceRange(start, end, snippet), TextRange(start + snippet.length))
    }

    fun indent() {
        if (running) return
        val lineStart = lineStartOf(code.text, code.selection.start)
        code = TextFieldValue(
            code.text.replaceRange(lineStart, lineStart, INDENT),
            TextRange(code.selection.start + INDENT.length),
        )
    }

    fun outdent() {
        if (running) return
        val lineStart = lineStartOf(code.text, code.selection.start)
        val spaces = code.text.drop(lineStart).take(INDENT.length).takeWhile { it == ' ' }.length
        if (spaces == 0) return
        code = TextFieldValue(
            code.text.removeRange(lineStart, lineStart + spaces),
            TextRange((code.selection.start - spaces).coerceAtLeast(lineStart)),
        )
    }

    // ---- Çalıştırma ----

    fun run() {
        stepMode = false
        start()
    }

    /** Durmuşken: adım adım modda başlatır. Çalışırken: bir sonraki satıra geçer. */
    fun step() {
        if (running) {
            stepMode = true
            stepSignal.trySend(Unit)
        } else {
            stepMode = true
            start()
        }
    }

    fun resume() {
        stepMode = false
        stepSignal.trySend(Unit)
        publish()
    }

    fun stop() {
        runJob?.cancel()
    }

    fun cycleSpeed() {
        speed = SimSpeed.entries[(speed.ordinal + 1) % SimSpeed.entries.size]
        publish()
    }

    private fun start() {
        if (running) return
        when (val result = game.compile(code.text)) {
            is CompileResult.Failure -> {
                errorLines = result.errors.map { it.line }.filter { it > 0 }.toSet()
                result.errors.forEach { log(it.display(), isError = true) }
                publish()
            }
            is CompileResult.Success -> {
                errorLines = emptySet()
                while (stepSignal.tryReceive().isSuccess) Unit
                running = true
                publish()
                runJob = viewModelScope.launch {
                    try {
                        game.run(result.program, Pacer())
                        log("Program bitti.")
                    } catch (e: ScriptError) {
                        if (e.line > 0) errorLines = setOf(e.line)
                        log(e.display(), isError = true)
                    } finally {
                        running = false
                        stepMode = false
                        currentLine = null
                        save()
                        publish()
                    }
                }
            }
        }
    }

    /** Oyun zamanını gerçek zamana çevirir: hız ayarı ve adım modu burada uygulanır. */
    private inner class Pacer : RunListener {
        private var pendingMs = 0.0
        private var lastFrame = System.nanoTime()

        override suspend fun onStatement(line: Int) {
            currentLine = line
            if (stepMode) {
                publish()
                stepSignal.receive()
            }
        }

        override suspend fun onTicks(ticks: Long) {
            if (speed == SimSpeed.MAX) {
                if (System.nanoTime() - lastFrame > MAX_SPEED_FRAME_NS) {
                    publish()
                    delay(1)
                    lastFrame = System.nanoTime()
                }
                return
            }
            pendingMs += ticks * BASE_MS_PER_TICK / speed.factor
            if (pendingMs >= FRAME_MS) {
                publish()
                val wait = pendingMs.toLong()
                pendingMs -= wait
                delay(wait)
            }
        }

        override fun onPrint(text: String) = log(text)
    }

    // ---- Kilitler ----

    fun buy(id: String) {
        if (game.buy(id)) {
            log("${Unlocks.get(id).title} açıldı!")
            save()
            publish()
        }
    }

    // ---- Kayıt ----

    fun save() {
        prefs.edit().putString(KEY_SAVE, game.progress.toSave(code.text).toJson()).apply()
    }

    override fun onCleared() {
        save()
        super.onCleared()
    }

    // ---- Durum ----

    private fun log(text: String, isError: Boolean = false) {
        console.addLast(ConsoleLine(text, isError))
        while (console.size > MAX_CONSOLE_LINES) console.removeFirst()
    }

    private fun publish() {
        _state.value = buildState()
    }

    private fun buildState(): GameUiState {
        val progress = game.progress
        return GameUiState(
            world = game.snapshot(),
            inventory = progress.inventory(),
            running = running,
            stepping = running && stepMode,
            currentLine = currentLine,
            errorLines = errorLines,
            console = console.toList(),
            speed = speed,
            unlocks = Unlocks.all.map { def ->
                UnlockRow(
                    def = def,
                    level = progress.level(def.id),
                    nextCost = progress.nextCost(def.id),
                    missing = def.requires.filterNot(progress::isUnlocked).map { Unlocks.get(it).title },
                    canBuy = progress.canBuy(def.id),
                )
            },
            unlockedIds = Unlocks.all.filter { progress.isUnlocked(it.id) }.map { it.id }.toSet(),
            features = progress.allowedFeatures(),
        )
    }

    companion object {
        private const val PREFS_NAME = "astro_miner"
        private const val KEY_SAVE = "save"

        /** 1× hızda bir oyun tick'inin gerçek süresi (ms). Hareket = 100 tick = 200 ms. */
        private const val BASE_MS_PER_TICK = 2.0
        private const val FRAME_MS = 16.0
        private const val IDLE_FRAME_MS = 100L
        private const val MAX_SPEED_FRAME_NS = 12_000_000L
        private const val MAX_CONSOLE_LINES = 50
        private const val INDENT = "    "

        private const val DEFAULT_CODE = "# Çalıştır'a bas: drone altındaki kayayı kazar.\n" +
            "# Kilitler ekranından yeni komutlar ve dil özellikleri aç.\n" +
            "mine()\n"

        private fun lineStartOf(text: String, cursor: Int): Int =
            text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0 || cursor == 0) 0 else it + 1 }

        /** Enter'a basılınca önceki satırın girintisini korur; satır ':' ile bitiyorsa bir seviye ekler. */
        fun autoIndent(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
            val pos = new.selection.start
            val insertedNewline = new.selection.collapsed &&
                new.text.length == old.text.length + 1 &&
                pos > 0 && new.text[pos - 1] == '\n' &&
                new.text.removeRange(pos - 1, pos) == old.text
            if (!insertedNewline) return new

            val prevStart = new.text.lastIndexOf('\n', pos - 2).let { if (pos < 2) 0 else it + 1 }
            val prevLine = new.text.substring(prevStart, pos - 1)
            var indent = prevLine.takeWhile { it == ' ' }
            if (prevLine.trimEnd().endsWith(":")) indent += INDENT
            if (indent.isEmpty()) return new
            return TextFieldValue(new.text.replaceRange(pos, pos, indent), TextRange(pos + indent.length))
        }
    }
}
