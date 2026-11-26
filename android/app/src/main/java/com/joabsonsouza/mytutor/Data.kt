package com.joabsonsouza.mytutor

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Um assunto que você gosta. `english` vai para o prompt. */
data class Interest(val id: String, val label: String, val english: String) {
    companion object {
        val all = listOf(
            Interest("travel", "Viagens", "travel"),
            Interest("work", "Trabalho", "work and career"),
            Interest("interview", "Entrevista de emprego", "job interviews"),
            Interest("tech", "Tecnologia", "technology"),
            Interest("movies", "Filmes e séries", "movies and TV series"),
            Interest("music", "Música", "music"),
            Interest("sports", "Esportes", "sports"),
            Interest("games", "Games", "video games"),
            Interest("food", "Comida", "food and cooking"),
            Interest("books", "Livros", "books"),
            Interest("business", "Negócios", "business"),
            Interest("fitness", "Saúde e treino", "health and fitness"),
            Interest("daily", "Dia a dia", "daily life"),
            Interest("news", "Notícias", "the news"),
        )
    }
}

/** Abre os arquivos do app uma vez (chamado ao iniciar). */
object AppData {
    lateinit var prefs: SharedPreferences
    lateinit var files: File
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("mytutor", Context.MODE_PRIVATE)
        files = context.filesDir
        Profile.load()
    }
}

/** Seu perfil (nome, foto, interesses), guardado no aparelho. */
object Profile {
    const val MIN_INTERESTS = 3

    var name by mutableStateOf("")
        private set
    var interests by mutableStateOf(listOf<String>())
        private set
    var photo by mutableStateOf<Bitmap?>(null)
        private set
    var onboarded by mutableStateOf(false)
        private set

    private val photoFile get() = File(AppData.files, "profile-photo.jpg")

    fun load() {
        val p = AppData.prefs
        name = p.getString("profileName", "") ?: ""
        interests = p.getString("profileInterests", null)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        onboarded = p.getBoolean("profileOnboarded", false)
        photo = photoFile.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    /** Ingleses dos interesses escolhidos, na ordem da lista. */
    val interestTopics get() = Interest.all.filter { it.id in interests }.map { it.english }

    fun save(name: String, photo: Bitmap?, interests: List<String>) {
        this.name = name.trim()
        this.interests = interests
        this.photo = photo
        onboarded = true
        AppData.prefs.edit()
            .putString("profileName", this.name)
            .putString("profileInterests", interests.joinToString(","))
            .putBoolean("profileOnboarded", true)
            .apply()
        if (photo != null) photoFile.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 80, it) } else photoFile.delete()
    }
}

/** Recorte quadrado do centro, reduzido (a foto só aparece pequena no chat). */
fun Bitmap.squareThumbnail(side: Int = 300): Bitmap {
    val crop = minOf(width, height)
    val square = Bitmap.createBitmap(this, (width - crop) / 2, (height - crop) / 2, crop, crop)
    return Bitmap.createScaledBitmap(square, side, side, true)
}

// region Conversas salvas

/** Uma conversa guardada no histórico. */
data class SavedConversation(
    val id: String = UUID.randomUUID().toString(),
    val savedAt: Long,
    /** "ana" ou "peter". */
    val tutor: String,
    val lines: List<Line>,
) {
    /** Primeira coisa que você disse (ou a primeira fala do tutor), para identificar a conversa. */
    val title: String
        get() = (lines.firstOrNull { it.role == Line.Role.Me } ?: lines.firstOrNull())?.text?.trim() ?: "Conversa"
}

/** Histórico de conversas salvas, guardado no próprio aparelho. */
object Storage {
    private const val KEY = "savedConversations"

    /** Todas as conversas, da mais recente para a mais antiga. */
    fun all(): List<SavedConversation> {
        val raw = AppData.prefs.getString(KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val lines = o.optJSONArray("lines") ?: JSONArray()
            SavedConversation(
                id = o.optString("id"),
                savedAt = o.optLong("savedAt"),
                tutor = o.optString("tutor", "ana"),
                lines = (0 until lines.length()).map { j ->
                    val l = lines.getJSONObject(j)
                    Line(if (l.optString("role") == "me") Line.Role.Me else Line.Role.Tutor, l.optString("text"))
                },
            )
        }.sortedByDescending { it.savedAt }
    }

    /** Salva (ou atualiza, se já existir com o mesmo id). */
    fun save(conversation: SavedConversation) = write(all().filter { it.id != conversation.id } + conversation)

    fun delete(id: String) = write(all().filter { it.id != id })

    private fun write(list: List<SavedConversation>) {
        val array = JSONArray()
        list.forEach { c ->
            val lines = JSONArray()
            c.lines.forEach { lines.put(JSONObject().put("role", if (it.role == Line.Role.Me) "me" else "tutor").put("text", it.text)) }
            array.put(JSONObject().put("id", c.id).put("savedAt", c.savedAt).put("tutor", c.tutor).put("lines", lines))
        }
        AppData.prefs.edit().putString(KEY, array.toString()).apply()
    }
}

// endregion

// region Flashcards

/** Um flashcard: palavra ou expressão em inglês, tradução e a frase da conversa como exemplo. */
data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val front: String,
    val back: String,
    val example: String,
    /** Nível de 0 a 5: sobe quando você acerta, volta a 0 quando erra. */
    val level: Int = 0,
    /** Quando o cartão aparece de novo na revisão (milissegundos). */
    val due: Long = System.currentTimeMillis(),
)

/** Cartões guardados no próprio aparelho, com repetição simples (estilo Anki). */
object FlashcardStore {
    private const val KEY = "flashcards"
    /** Dias até a próxima revisão para cada nível (nível 0 = hoje mesmo). */
    private val days = listOf(0, 1, 3, 7, 15, 30)

    fun all(): List<Flashcard> {
        val raw = AppData.prefs.getString(KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Flashcard(o.optString("id"), o.optString("front"), o.optString("back"), o.optString("example"), o.optInt("level"), o.optLong("due"))
        }
    }

    /** Cartões para revisar agora, dos mais atrasados para os mais novos. */
    fun due(): List<Flashcard> = all().filter { it.due <= System.currentTimeMillis() }.sortedBy { it.due }

    fun add(card: Flashcard) = write(all() + card)

    fun delete(id: String) = write(all().filter { it.id != id })

    /** Acertou: sobe de nível e volta daqui a alguns dias. Errou: volta ao nível 0 e reaparece hoje. */
    fun review(card: Flashcard, correct: Boolean): Flashcard {
        val level = if (correct) minOf(card.level + 1, days.size - 1) else 0
        val updated = card.copy(level = level, due = System.currentTimeMillis() + days[level] * 86_400_000L)
        write(all().map { if (it.id == card.id) updated else it })
        return updated
    }

    private fun write(cards: List<Flashcard>) {
        val array = JSONArray()
        cards.forEach {
            array.put(JSONObject().put("id", it.id).put("front", it.front).put("back", it.back)
                .put("example", it.example).put("level", it.level).put("due", it.due))
        }
        AppData.prefs.edit().putString(KEY, array.toString()).apply()
    }
}

// endregion
