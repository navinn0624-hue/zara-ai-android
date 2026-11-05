package com.zara.assistant.engine

import android.content.Context
import android.provider.ContactsContract

object ContactsHelper {

    fun findPhoneNumber(context: Context, queryName: String): String? {
        val cleanQuery = queryName.lowercase().trim()
        val contentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                var bestMatchNumber: String? = null

                while (it.moveToNext()) {
                    val name = (it.getString(nameIdx) ?: "").lowercase().trim()
                    val number = it.getString(numIdx) ?: ""

                    // Exact match
                    if (name == cleanQuery) {
                        return cleanPhoneNumber(number)
                    }

                    // Partial match (e.g. "Amma" in "Amma Jio")
                    if (name.contains(cleanQuery) || cleanQuery.contains(name)) {
                        bestMatchNumber = cleanPhoneNumber(number)
                    }
                }
                return bestMatchNumber
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun cleanPhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9+]"), "")
    }
}
