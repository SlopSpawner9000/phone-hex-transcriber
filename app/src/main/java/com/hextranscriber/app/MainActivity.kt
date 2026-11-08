package com.hextranscriber.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset

private enum class Base { HEX, DEC, BIN, MORSE }

private val MORSE_TO_CHAR: Map<String, Char> = mapOf(
    ".-" to 'A', "-..." to 'B', "-.-." to 'C', "-.." to 'D', "." to 'E',
    "..-." to 'F', "--." to 'G', "...." to 'H', ".." to 'I', ".---" to 'J',
    "-.-" to 'K', ".-.." to 'L', "--" to 'M', "-." to 'N', "---" to 'O',
    ".--." to 'P', "--.-" to 'Q', ".-." to 'R', "..." to 'S', "-" to 'T',
    "..-" to 'U', "...-" to 'V', ".--" to 'W', "-..-" to 'X', "-.--" to 'Y',
    "--.." to 'Z',
    "-----" to '0', ".----" to '1', "..---" to '2', "...--" to '3', "....-" to '4',
    "....." to '5', "-...." to '6', "--..." to '7', "---.." to '8', "----." to '9'
)

private val CHAR_TO_MORSE: Map<Char, String> = MORSE_TO_CHAR.entries.associate { (k, v) -> v to k }

class MainActivity : Activity() {

    private var currentBase = Base.HEX
    private var isForward = true // true: base -> text (custom keypad). false: text -> base (system keyboard)
    private var morseAutoGuess = false // best-effort letter segmentation when no manual separators are typed

    private val hexBuffer = StringBuilder()
    private val decBuffer = StringBuilder()
    private val binBuffer = StringBuilder()
    private val morseBuffer = StringBuilder()

    private lateinit var tvInput: TextView
    private lateinit var etInputText: EditText
    private lateinit var tvOutput: TextView
    private lateinit var baseInputScroll: HorizontalScrollView
    private lateinit var hexKeypad: View
    private lateinit var decKeypad: View
    private lateinit var binKeypad: View
    private lateinit var morseKeypad: View
    private lateinit var btnModeToggle: Button
    private lateinit var btnBaseHex: Button
    private lateinit var btnBaseDec: Button
    private lateinit var btnBaseBin: Button
    private lateinit var btnBaseMorse: Button
    private lateinit var btnMorseAutoGuess: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvInput = findViewById(R.id.tvInput)
        etInputText = findViewById(R.id.etInputText)
        tvOutput = findViewById(R.id.tvOutput)
        baseInputScroll = findViewById(R.id.baseInputScroll)
        hexKeypad = findViewById(R.id.hexKeypad)
        decKeypad = findViewById(R.id.decKeypad)
        binKeypad = findViewById(R.id.binKeypad)
        morseKeypad = findViewById(R.id.morseKeypad)
        btnModeToggle = findViewById(R.id.btnModeToggle)
        btnBaseHex = findViewById(R.id.btnBaseHex)
        btnBaseDec = findViewById(R.id.btnBaseDec)
        btnBaseBin = findViewById(R.id.btnBaseBin)
        btnBaseMorse = findViewById(R.id.btnBaseMorse)
        btnMorseAutoGuess = findViewById(R.id.btnMorseAutoGuess)

        wireHexKeys()
        wireDecKeys()
        wireBinKeys()
        wireMorseKeys()
        wireControls()
        wireBaseSelector()

        btnMorseAutoGuess.setOnClickListener {
            morseAutoGuess = !morseAutoGuess
            refreshUi()
        }

        etInputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isForward) updateOutputFromText(s?.toString().orEmpty())
            }
        })

        btnModeToggle.setOnClickListener { toggleDirection() }
        findViewById<Button>(R.id.btnCopyOutput).setOnClickListener { copyOutput() }

        refreshUi()
    }

    private fun wireHexKeys() {
        val ids = intArrayOf(
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9,
            R.id.keyA, R.id.keyB, R.id.keyC, R.id.keyD, R.id.keyE, R.id.keyF
        )
        for (id in ids) {
            val button = findViewById<Button>(id)
            button.setOnClickListener {
                hexBuffer.append(button.text.toString())
                refreshUi()
            }
        }
    }

    private fun wireDecKeys() {
        val digitIds = intArrayOf(
            R.id.decKey0, R.id.decKey1, R.id.decKey2, R.id.decKey3, R.id.decKey4,
            R.id.decKey5, R.id.decKey6, R.id.decKey7, R.id.decKey8, R.id.decKey9
        )
        for (id in digitIds) {
            val button = findViewById<Button>(id)
            button.setOnClickListener {
                decBuffer.append(button.text.toString())
                refreshUi()
            }
        }
        findViewById<Button>(R.id.decKeySpace).setOnClickListener {
            if (decBuffer.isNotEmpty() && decBuffer.last() != ' ') {
                decBuffer.append(' ')
                refreshUi()
            }
        }
    }

    private fun wireBinKeys() {
        findViewById<Button>(R.id.binKey0).setOnClickListener {
            binBuffer.append('0')
            refreshUi()
        }
        findViewById<Button>(R.id.binKey1).setOnClickListener {
            binBuffer.append('1')
            refreshUi()
        }
    }

    private fun wireMorseKeys() {
        findViewById<Button>(R.id.morseKeyDot).setOnClickListener {
            morseBuffer.append('.')
            refreshUi()
        }
        findViewById<Button>(R.id.morseKeyDash).setOnClickListener {
            morseBuffer.append('-')
            refreshUi()
        }
        findViewById<Button>(R.id.morseKeyLetterSpace).setOnClickListener {
            if (morseBuffer.isNotEmpty() && morseBuffer.last() != ' ' && morseBuffer.last() != '/') {
                morseBuffer.append(' ')
                refreshUi()
            }
        }
        findViewById<Button>(R.id.morseKeyWordSpace).setOnClickListener {
            if (morseBuffer.isNotEmpty() && morseBuffer.last() != '/') {
                morseBuffer.append('/')
                refreshUi()
            }
        }
    }

    private fun wireControls() {
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            if (isForward) {
                currentBuffer().clear()
                refreshUi()
            } else {
                etInputText.text.clear()
            }
        }
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            if (isForward) {
                val buffer = currentBuffer()
                if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
                refreshUi()
            } else {
                val text = etInputText.text
                if (text.isNotEmpty()) text.delete(text.length - 1, text.length)
            }
        }
    }

    private fun wireBaseSelector() {
        btnBaseHex.setOnClickListener { selectBase(Base.HEX) }
        btnBaseDec.setOnClickListener { selectBase(Base.DEC) }
        btnBaseBin.setOnClickListener { selectBase(Base.BIN) }
        btnBaseMorse.setOnClickListener { selectBase(Base.MORSE) }
    }

    private fun selectBase(base: Base) {
        currentBase = base
        highlightBaseTab(btnBaseHex, base == Base.HEX)
        highlightBaseTab(btnBaseDec, base == Base.DEC)
        highlightBaseTab(btnBaseBin, base == Base.BIN)
        highlightBaseTab(btnBaseMorse, base == Base.MORSE)
        refreshUi()
    }

    private fun highlightBaseTab(button: Button, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.btn_accent_filled else R.drawable.btn_accent_outline)
        button.setTextColor(getColor(if (selected) R.color.text_on_accent else R.color.accent))
    }

    private fun currentBuffer(): StringBuilder = when (currentBase) {
        Base.HEX -> hexBuffer
        Base.DEC -> decBuffer
        Base.BIN -> binBuffer
        Base.MORSE -> morseBuffer
    }

    private fun toggleDirection() {
        isForward = !isForward
        refreshUi()
    }

    private fun refreshUi() {
        hexKeypad.visibility = if (isForward && currentBase == Base.HEX) View.VISIBLE else View.GONE
        decKeypad.visibility = if (isForward && currentBase == Base.DEC) View.VISIBLE else View.GONE
        binKeypad.visibility = if (isForward && currentBase == Base.BIN) View.VISIBLE else View.GONE
        morseKeypad.visibility = if (isForward && currentBase == Base.MORSE) View.VISIBLE else View.GONE
        baseInputScroll.visibility = if (isForward) View.VISIBLE else View.GONE

        btnMorseAutoGuess.visibility = if (isForward && currentBase == Base.MORSE) View.VISIBLE else View.GONE
        btnMorseAutoGuess.text = getString(if (morseAutoGuess) R.string.morse_auto_guess_on else R.string.morse_auto_guess_off)
        btnMorseAutoGuess.setBackgroundResource(if (morseAutoGuess) R.drawable.btn_accent_filled else R.drawable.btn_accent_outline)
        btnMorseAutoGuess.setTextColor(getColor(if (morseAutoGuess) R.color.text_on_accent else R.color.accent))
        etInputText.visibility = if (isForward) View.GONE else View.VISIBLE

        val baseLabel = baseLabel(currentBase)
        btnModeToggle.text = if (isForward) "⇄ TEXT→$baseLabel" else "⇄ $baseLabel→TEXT"

        if (isForward) {
            val buffer = currentBuffer()
            tvInput.text = if (buffer.isEmpty()) {
                getString(R.string.hint_hex_empty)
            } else when (currentBase) {
                Base.HEX -> formatGroups(buffer.toString(), 2)
                Base.BIN -> formatGroups(buffer.toString(), 8)
                Base.DEC -> buffer.toString()
                Base.MORSE -> formatMorseDisplay(buffer.toString())
            }
            tvOutput.text = when (currentBase) {
                Base.HEX -> decodeHexToText(buffer.toString())
                Base.DEC -> decodeDecToText(buffer.toString())
                Base.BIN -> decodeBinToText(buffer.toString())
                Base.MORSE -> decodeMorseToText(buffer.toString(), morseAutoGuess)
            }
            baseInputScroll.post { baseInputScroll.fullScroll(View.FOCUS_RIGHT) }
        } else {
            updateOutputFromText(etInputText.text.toString())
        }
    }

    private fun baseLabel(base: Base): String = when (base) {
        Base.HEX -> "HEX"
        Base.DEC -> "DEC"
        Base.BIN -> "BIN"
        Base.MORSE -> "MORSE"
    }

    private fun updateOutputFromText(text: String) {
        tvOutput.text = when (currentBase) {
            Base.HEX -> encodeTextToHex(text)
            Base.DEC -> encodeTextToDec(text)
            Base.BIN -> encodeTextToBin(text)
            Base.MORSE -> encodeTextToMorse(text)
        }
    }

    private fun formatMorseDisplay(raw: String): String = raw.replace("/", " / ")

    private fun formatGroups(raw: String, groupSize: Int): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            if (i > 0) sb.append(' ')
            val end = minOf(i + groupSize, raw.length)
            sb.append(raw.substring(i, end))
            i += groupSize
        }
        return sb.toString()
    }

    private fun decodeHexToText(hex: String): String {
        val fullLength = hex.length - (hex.length % 2)
        if (fullLength <= 0) return ""
        val byteCount = fullLength / 2
        val bytes = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            val pair = hex.substring(i * 2, i * 2 + 2)
            bytes[i] = pair.toInt(16).toByte()
        }
        return bytesToDisplayText(bytes)
    }

    private fun decodeDecToText(dec: String): String {
        val hasTrailingSpace = dec.endsWith(" ")
        val tokens = dec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val completeTokens = if (hasTrailingSpace || tokens.isEmpty()) tokens else tokens.dropLast(1)
        if (completeTokens.isEmpty()) return ""
        val bytes = ByteArray(completeTokens.size)
        for ((i, token) in completeTokens.withIndex()) {
            val value = token.toIntOrNull()
            bytes[i] = if (value != null && value in 0..255) value.toByte() else '.'.code.toByte()
        }
        return bytesToDisplayText(bytes)
    }

    private fun decodeBinToText(bin: String): String {
        val fullLength = bin.length - (bin.length % 8)
        if (fullLength <= 0) return ""
        val byteCount = fullLength / 8
        val bytes = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            val chunk = bin.substring(i * 8, i * 8 + 8)
            bytes[i] = chunk.toInt(2).toByte()
        }
        return bytesToDisplayText(bytes)
    }

    private fun decodeMorseToText(morse: String, autoGuess: Boolean): String {
        if (morse.isEmpty()) return ""
        val hasTrailingSeparator = morse.last() == ' ' || morse.last() == '/'
        val words = morse.split('/')
        val sb = StringBuilder()
        for ((index, word) in words.withIndex()) {
            if (index > 0) sb.append(' ')
            val tokens = word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
            val isTrailing = index == words.lastIndex && !hasTrailingSeparator && tokens.isNotEmpty()
            val trailingToken = if (isTrailing) tokens.removeAt(tokens.lastIndex) else null

            for (token in tokens) {
                sb.append(decodeFinalizedMorseToken(token, autoGuess))
            }
            if (trailingToken != null) {
                sb.append(decodeInProgressMorseToken(trailingToken, autoGuess))
            }
        }
        return sb.toString()
    }

    // A token the user has already moved past (an explicit separator follows, or it
    // isn't the very last token of the buffer): must parse fully, or it's shown as "."
    private fun decodeFinalizedMorseToken(token: String, autoGuess: Boolean): String {
        MORSE_TO_CHAR[token]?.let { return it.toString() }
        if (!autoGuess) return "."
        val parse = morseDpTable(token)[token.length] ?: return "."
        return morseCodesToText(parse)
    }

    // The token still being typed (no separator yet): with auto-guess, decode the
    // longest prefix that cleanly segments into letters and leave the undecided tail
    // (which might still grow into another letter) pending until more keys are pressed.
    private fun decodeInProgressMorseToken(token: String, autoGuess: Boolean): String {
        if (!autoGuess) return ""
        val dp = morseDpTable(token)
        for (i in dp.size - 1 downTo 0) {
            val parse = dp[i] ?: continue
            return morseCodesToText(parse)
        }
        return ""
    }

    // dp[i] = a valid split of token[0, i) into complete Morse codes, if one exists
    // (longest-code-first, so ambiguous runs like "...." prefer one letter over two).
    private fun morseDpTable(token: String): Array<List<String>?> {
        val n = token.length
        val dp = arrayOfNulls<List<String>>(n + 1)
        dp[0] = emptyList()
        for (i in 1..n) {
            for (len in minOf(5, i) downTo 1) {
                val prev = dp[i - len] ?: continue
                val piece = token.substring(i - len, i)
                if (MORSE_TO_CHAR.containsKey(piece)) {
                    dp[i] = prev + piece
                    break
                }
            }
        }
        return dp
    }

    private fun morseCodesToText(codes: List<String>): String =
        codes.joinToString("") { (MORSE_TO_CHAR[it] ?: '.').toString() }

    private fun bytesToDisplayText(bytes: ByteArray): String {
        val decoded = String(bytes, Charset.forName("UTF-8"))
        val sb = StringBuilder(decoded.length)
        for (ch in decoded) {
            val code = ch.code
            val isControl = (code in 0x00..0x1F && ch != '\n' && ch != '\t') || code == 0x7F
            sb.append(if (isControl) '.' else ch)
        }
        return sb.toString()
    }

    private fun encodeTextToHex(text: String): String {
        if (text.isEmpty()) return ""
        val bytes = text.toByteArray(Charset.forName("UTF-8"))
        val sb = StringBuilder(bytes.size * 3)
        for ((index, b) in bytes.withIndex()) {
            if (index > 0) sb.append(' ')
            sb.append(String.format("%02X", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun encodeTextToDec(text: String): String {
        if (text.isEmpty()) return ""
        val bytes = text.toByteArray(Charset.forName("UTF-8"))
        val sb = StringBuilder()
        for ((index, b) in bytes.withIndex()) {
            if (index > 0) sb.append(' ')
            sb.append(b.toInt() and 0xFF)
        }
        return sb.toString()
    }

    private fun encodeTextToBin(text: String): String {
        if (text.isEmpty()) return ""
        val bytes = text.toByteArray(Charset.forName("UTF-8"))
        val sb = StringBuilder(bytes.size * 8)
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            sb.append(value.toString(2).padStart(8, '0'))
        }
        return sb.toString()
    }

    private fun encodeTextToMorse(text: String): String {
        if (text.isEmpty()) return ""
        val words = text.uppercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.joinToString("/") { word ->
            word.mapNotNull { CHAR_TO_MORSE[it] }.joinToString(" ")
        }
    }

    private fun copyOutput() {
        val output = tvOutput.text.toString()
        if (output.isEmpty()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hex Transcriber output", output))
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }
}
