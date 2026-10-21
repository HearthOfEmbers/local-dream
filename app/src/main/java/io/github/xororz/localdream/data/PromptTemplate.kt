package io.github.xororz.localdream.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/**
 * A named starting prompt: a prompt / negative-prompt pair the user saved and
 * can apply (replace both fields) or append to the current fields with one tap.
 */
@Immutable
data class PromptTemplate(
    val name: String,
    val prompt: String,
    val negativePrompt: String,
) {
    companion object {
        fun encodeList(templates: List<PromptTemplate>): String {
            val array = JSONArray()
            templates.forEach { template ->
                array.put(
                    JSONObject()
                        .put("name", template.name)
                        .put("prompt", template.prompt)
                        .put("negative_prompt", template.negativePrompt),
                )
            }
            return array.toString()
        }

        fun decodeList(raw: String?): List<PromptTemplate> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val obj = array.optJSONObject(index) ?: return@mapNotNull null
                    val name = obj.optString("name").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    PromptTemplate(
                        name = name,
                        prompt = obj.optString("prompt"),
                        negativePrompt = obj.optString("negative_prompt"),
                    )
                }
            } catch (_: org.json.JSONException) {
                emptyList()
            }
        }

        /**
         * Appends [addition] to [current] with a ", " separator, skipping the
         * separator when either side is empty or [current] already ends in one.
         */
        fun appendTo(current: String, addition: String): String {
            val add = addition.trim()
            if (add.isEmpty()) return current
            val base = current.trimEnd()
            if (base.isEmpty()) return add
            // Don't stack the same text twice (e.g. an identical negative prompt).
            if (base.contains(add)) return current
            val separator = if (base.endsWith(",")) " " else ", "
            return base + separator + add
        }
    }
}
