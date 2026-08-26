package com.victorkoffed.projektandroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central persistence layer for the Coffee Journal application.
 * Manages local storage of brewing configurations, execution data, and derived metrics.
 */
@Database(
    entities = [
        Grinder::class,
        Method::class,
        Bean::class,
        Brew::class,
        BrewSample::class
    ],
    views = [BrewMetrics::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CoffeeDatabase : RoomDatabase() {

    abstract fun coffeeDao(): CoffeeDao

    companion object {
        @Volatile
        private var INSTANCE: CoffeeDatabase? = null

        fun getInstance(context: Context): CoffeeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CoffeeDatabase::class.java,
                    "coffee_journal.db"
                )
                    .addCallback(DatabaseCallback)
                    .addMigrations(
                        Migrations.MIGRATION_4_5
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Enforces database-level constraints and seeds default domain data upon initial creation.
         */
        private val DatabaseCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // Enforces cascading deletes across related tables (e.g., deleting a Bean removes its Brews).
                db.execSQL("PRAGMA foreign_keys = ON;")

                db.execSQL("INSERT INTO Method (name) VALUES ('V60');")
                db.execSQL("INSERT INTO Method (name) VALUES ('Aeropress');")
            }
        }
    }
}

/**
 * Registry for database schema migrations.
 * Ensures data preservation during structural changes across version increments.
 */
object Migrations {

    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // db.execSQL("ALTER TABLE 'Bean' ADD COLUMN 'new_column' INTEGER NOT NULL DEFAULT 0")
        }
    }
}