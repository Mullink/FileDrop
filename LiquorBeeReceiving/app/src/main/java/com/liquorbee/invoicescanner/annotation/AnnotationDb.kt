package com.liquorbee.invoicescanner.annotation

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A reusable, LOCAL-ONLY drawing template a user creates once and applies to future photos - e.g.
 * a saved receiving checklist or shelf diagram with a starting set of marks already drawn on it.
 * Per the approved plan this never touches the backend: it lives entirely in this device's Room
 * database. Only the final flattened photo (base image + all strokes/notes composited in) is ever
 * uploaded, via the existing InvoiceOcrScan/ScanInvoice endpoint - the server has no concept of
 * "templates" in this sense at all.
 */
@Entity(tableName = "drawing_templates")
data class DrawingTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val baseImageBytes: ByteArray,
    val strokesJson: String = "[]",
    val notesJson: String = "[]",
    val createdAtUtcMillis: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean = this === other || (other is DrawingTemplateEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
}

@Dao
interface DrawingTemplateDao {
    @Query("SELECT id, name, strokesJson, notesJson, createdAtUtcMillis, baseImageBytes FROM drawing_templates ORDER BY createdAtUtcMillis DESC")
    suspend fun getAll(): List<DrawingTemplateEntity>

    @Query("SELECT * FROM drawing_templates WHERE id = :id")
    suspend fun getById(id: Long): DrawingTemplateEntity?

    @Insert
    suspend fun insert(template: DrawingTemplateEntity): Long

    @Query("DELETE FROM drawing_templates WHERE id = :id")
    suspend fun delete(id: Long)
}

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun strokesFromJson(json: String): List<Stroke> {
        val type = object : TypeToken<List<Stroke>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    @TypeConverter
    fun strokesToJson(strokes: List<Stroke>): String = gson.toJson(strokes)
}

@Database(entities = [DrawingTemplateEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AnnotationDatabase : RoomDatabase() {
    abstract fun drawingTemplateDao(): DrawingTemplateDao

    companion object {
        @Volatile private var instance: AnnotationDatabase? = null

        fun getInstance(context: Context): AnnotationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnnotationDatabase::class.java,
                    "annotation_templates.db"
                ).build().also { instance = it }
            }
    }
}

/** Convenience (de)serialization for the JSON columns, using Gson directly rather than only
 * through Room's TypeConverters, since the drawing screen also needs to read/write these outside
 * of a Room query result (e.g. building a Stroke list live while the user is drawing). */
object AnnotationJson {
    private val gson = Gson()

    fun strokesFrom(json: String): MutableList<Stroke> {
        val type = object : TypeToken<List<Stroke>>() {}.type
        return gson.fromJson<List<Stroke>>(json, type)?.toMutableList() ?: mutableListOf()
    }

    fun toJson(strokes: List<Stroke>): String = gson.toJson(strokes)

    fun notesFrom(json: String): MutableList<TextNote> {
        val type = object : TypeToken<List<TextNote>>() {}.type
        return gson.fromJson<List<TextNote>>(json, type)?.toMutableList() ?: mutableListOf()
    }

    fun notesToJson(notes: List<TextNote>): String = gson.toJson(notes)
}
