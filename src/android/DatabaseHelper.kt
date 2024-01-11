package com.outsystems.experts.arcgis

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatabaseHelper(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        try {
            val createTableSql = """
            CREATE TABLE graphics_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                symbolJson TEXT
            )
        """.trimIndent()
            db.execSQL(createTableSql)
        } catch (ex: Exception) {
            Log.v("TAG", ex.message.toString())
        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun saveGraphicJson(jsonData: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = this@DatabaseHelper.writableDatabase
                    val contentValues = ContentValues().apply {
                        put("symbolJson", jsonData)
                    }
                    db.insert("graphics_table", null, contentValues)
                    db.close()
                }
            } catch (ex: Exception) {
                Log.v("TAG", ex.message.toString())
            }
        }
    }

    fun deleteAllGraphics() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = this@DatabaseHelper.writableDatabase
                    db.execSQL("DELETE FROM graphics_table")
                    db.close()
                }
            } catch (ex: Exception) {
                Log.v("TAG", ex.message.toString())
            }
        }
    }

    fun getAllGraphics(): List<String> {
        try {
            val jsonDataList = mutableListOf<String>()
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM graphics_table", null)

            val columnIndex = cursor.getColumnIndex("symbolJson")
            if (columnIndex != -1) {
                if (cursor.moveToFirst()) {
                    do {
                        val jsonData = cursor.getString(columnIndex)
                        jsonDataList.add(jsonData)
                    } while (cursor.moveToNext())
                }
            }

            cursor.close()
            db.close()
            return jsonDataList
        } catch (ex: Exception) {
            return emptyList()
        }
    }

    companion object {
        private const val DATABASE_NAME = "graphics.db"
        private const val DATABASE_VERSION = 1
    }
}