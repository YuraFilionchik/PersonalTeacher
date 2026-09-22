package com.example.personallangmaster.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.personallangmaster.data.db.dao.BackupDao
import com.example.personallangmaster.data.db.dao.ContentDao
import com.example.personallangmaster.data.db.dao.LessonDao
import com.example.personallangmaster.data.db.dao.ProfileDao
import com.example.personallangmaster.data.db.dao.StatsDao
import com.example.personallangmaster.data.db.dao.VocabDao
import com.example.personallangmaster.data.db.entity.DailyStatEntity
import com.example.personallangmaster.data.db.entity.ExerciseAttemptEntity
import com.example.personallangmaster.data.db.entity.ExerciseEntity
import com.example.personallangmaster.data.db.entity.GrammarProgressEntity
import com.example.personallangmaster.data.db.entity.GrammarTopicEntity
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.data.db.entity.MinimalPairEntity
import com.example.personallangmaster.data.db.entity.MistakeEntity
import com.example.personallangmaster.data.db.entity.PhonemeEntity
import com.example.personallangmaster.data.db.entity.PhonemeScoreEntity
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.db.entity.PronunciationAttemptEntity
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import com.example.personallangmaster.data.db.entity.StreakEntity
import com.example.personallangmaster.data.db.entity.TurnEntity
import com.example.personallangmaster.data.db.entity.UsageLogEntity
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.db.entity.VocabReviewEntity

@Database(
    entities = [
        ProfileEntity::class,
        LessonEntity::class,
        TurnEntity::class,
        MistakeEntity::class,
        VocabItemEntity::class,
        VocabReviewEntity::class,
        GrammarTopicEntity::class,
        GrammarProgressEntity::class,
        ExerciseEntity::class,
        ExerciseAttemptEntity::class,
        PhonemeEntity::class,
        MinimalPairEntity::class,
        PhonemeScoreEntity::class,
        PronunciationAttemptEntity::class,
        ScenarioEntity::class,
        UsageLogEntity::class,
        DailyStatEntity::class,
        StreakEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun lessonDao(): LessonDao
    abstract fun vocabDao(): VocabDao
    abstract fun contentDao(): ContentDao
    abstract fun statsDao(): StatsDao
    abstract fun backupDao(): BackupDao

    companion object {
        private const val NAME = "personallangmaster.db"

        /**
         * Пометка к уроку. Историю уроков терять нельзя, поэтому миграция
         * настоящая, а не сброс базы.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lesson ADD COLUMN note TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Внешние ключи чистят историю урока вместе с самим уроком.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
