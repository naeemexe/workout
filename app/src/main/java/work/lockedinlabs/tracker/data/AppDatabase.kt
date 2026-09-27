package work.lockedinlabs.tracker.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExerciseLog::class, PlanDay::class, DayChoice::class, Profile::class, WeightEntry::class, Plan::class, ProgressionRule::class, CustomExercise::class],
    version = 14,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = AppDatabase.DropProfilePhoto::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun planDao(): PlanDao
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun ruleDao(): ProgressionRuleDao
    abstract fun customExerciseDao(): CustomExerciseDao

    /** v6: custom photos replaced by built-in avatars (+ birth year added automatically). */
    @DeleteColumn(tableName = "profile", columnName = "photoPath")
    class DropProfilePhoto : AutoMigrationSpec

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lockedin.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_7_8)
                .build()

        /** v8: several plans. The existing week becomes "My plan" (active); its days get a planId. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, `activeSince` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO plans (id, name, isActive, activeSince, createdAt) VALUES (1, 'My plan', 1, 0, ?)",
                    arrayOf(System.currentTimeMillis()),
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_days_new` (`dayIndex` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`isRest` INTEGER NOT NULL, `exercises` TEXT NOT NULL, `planId` INTEGER NOT NULL, PRIMARY KEY(`planId`, `dayIndex`))",
                )
                db.execSQL("INSERT INTO plan_days_new (dayIndex, name, isRest, exercises, planId) SELECT dayIndex, name, isRest, exercises, 1 FROM plan_days")
                db.execSQL("DROP TABLE plan_days")
                db.execSQL("ALTER TABLE plan_days_new RENAME TO plan_days")
            }
        }

        /** v1 stored one weight/reps/sets per entry; v2 stores each set. Expand "50 lb × 10 × 2" into two sets. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE exercise_logs_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, exercise TEXT NOT NULL, sets TEXT NOT NULL, " +
                        "epochDay INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
                )
                db.query("SELECT id, exercise, weightLbs, reps, sets, epochDay, createdAt FROM exercise_logs").use { c ->
                    while (c.moveToNext()) {
                        val sets = List(c.getInt(4).coerceAtLeast(1)) { SetEntry(c.getDouble(2), c.getInt(3)) }
                        db.execSQL(
                            "INSERT INTO exercise_logs_new (id, exercise, sets, epochDay, createdAt) VALUES (?, ?, ?, ?, ?)",
                            arrayOf(c.getLong(0), c.getString(1), SetCodec.encode(sets), c.getLong(5), c.getLong(6)),
                        )
                    }
                }
                db.execSQL("DROP TABLE exercise_logs")
                db.execSQL("ALTER TABLE exercise_logs_new RENAME TO exercise_logs")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_logs_exercise ON exercise_logs (exercise)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_logs_epochDay ON exercise_logs (epochDay)")
            }
        }
    }
}
