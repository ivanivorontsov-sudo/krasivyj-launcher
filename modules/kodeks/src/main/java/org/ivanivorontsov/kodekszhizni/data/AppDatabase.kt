package org.ivanivorontsov.kodekszhizni.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter fun fromMeasureType(v: MeasureType): String = v.name
    @TypeConverter fun toMeasureType(v: String): MeasureType = MeasureType.valueOf(v)
    @TypeConverter fun fromMeasureStatus(v: MeasureStatus): String = v.name
    @TypeConverter fun toMeasureStatus(v: String): MeasureStatus = MeasureStatus.valueOf(v)
}

@Database(entities = [Article::class, Measure::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun measureDao(): MeasureDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kodeks_zhizni.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed after instance is ready via separate launch
                        }
                    })
                    .build()
                    .also { db ->
                        INSTANCE = db
                        CoroutineScope(Dispatchers.IO).launch {
                            if (db.articleDao().count() == 0) {
                                db.articleDao().insertAll(SeedData.articles())
                            }
                        }
                    }
            }
        }
    }
}
