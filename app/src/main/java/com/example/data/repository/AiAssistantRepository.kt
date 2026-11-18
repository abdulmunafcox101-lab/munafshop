package com.example.data.repository

import android.util.Log
import com.example.data.model.AppLanguage
import com.example.data.model.TransactionType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiAssistantRepo"

data class VoiceAccountingDraft(
    val txType: TransactionType,
    val amount: Double,
    val partyName: String,
    val itemName: String,
    val note: String,
    val needsClarification: Boolean,
    val clarificationQuestion: String
)

class AiAssistantRepository {

    private val assistantModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val voiceParserSchema = Schema.obj(
        properties = mapOf(
            "txType" to Schema.enumeration(
                values = listOf("SALE", "PURCHASE", "DUE_COLLECTION", "SUPPLIER_PAYMENT", "INCOME", "EXPENSE"),
                description = "Accounting transaction type"
            ),
            "amount" to Schema.double(description = "Total amount in BDT"),
            "partyName" to Schema.string(description = "Customer or supplier name if mentioned, else empty string"),
            "itemName" to Schema.string(description = "Product or expense category if mentioned, else empty string"),
            "note" to Schema.string(description = "Short summary note"),
            "needsClarification" to Schema.boolean(description = "True if amount or intent is ambiguous"),
            "clarificationQuestion" to Schema.string(description = "Clarifying question if needsClarification is true, else empty string")
        )
    )

    private val voiceParserModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceParserSchema
            }
        )
    }

    fun askBusinessAssistantStream(
        question: String,
        businessContextSnapshot: String,
        language: AppLanguage
    ): Flow<String> = flow {
        val langInstruction = if (language == AppLanguage.BN) {
            "Answer clearly and concisely in natural Bengali (বাংলা), using BDT (৳) currency."
        } else {
            "Answer clearly and concisely in English, using BDT (৳) currency."
        }

        val prompt = """
            You are the private read-only AI Business Assistant for MUNAF ONLINE SHOP.
            CRITICAL RULES:
            1. Answer ONLY using the authorized business snapshot provided below for this specific shop.
            2. Never invent numbers, never access or mention any other business, and NEVER claim to modify accounting entries.
            3. Keep cash collections separate from credit/due sales and profit.
            4. $langInstruction

            AUTHORIZED BUSINESS SNAPSHOT:
            $businessContextSnapshot

            USER QUESTION:
            $question
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            assistantModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI returned an empty response.")
        }
    }

    suspend fun parseVoiceCommandToDraft(
        spokenText: String,
        language: AppLanguage
    ): Result<VoiceAccountingDraft> {
        return try {
            val prompt = """
                Parse this Bangladeshi shopkeeper voice command into an accounting draft.
                Command: "$spokenText"
                Examples:
                - "আজ ৫০০ টাকার পণ্য বিক্রি করেছি" -> SALE, amount 500
                - "রহিম ২০০ টাকা জমা দিয়েছে" -> DUE_COLLECTION, partyName Rahim, amount 200
                - "আজ ৩০০ টাকা খরচ হয়েছে" -> EXPENSE, amount 300
                - "করিমের কাছে ১০০০ টাকা বাকি" -> SALE (credit/due), partyName Karim, amount 1000, note "Due Sale"
                If amount is <= 0 or intent is unclear, set needsClarification = true and write a helpful question in ${if (language == AppLanguage.BN) "Bengali" else "English"}.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                voiceParserModel.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("Empty AI response")
            val json = JSONObject(jsonString)
            Result.success(
                VoiceAccountingDraft(
                    txType = TransactionType.fromName(json.optString("txType", "SALE")),
                    amount = json.optDouble("amount", 0.0),
                    partyName = json.optString("partyName", ""),
                    itemName = json.optString("itemName", ""),
                    note = json.optString("note", spokenText),
                    needsClarification = json.optBoolean("needsClarification", false),
                    clarificationQuestion = json.optString("clarificationQuestion", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice parsing timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice parsing failed via Gemini; falling back to local heuristic if needed", e)
            Result.failure(e)
        }
    }
}
