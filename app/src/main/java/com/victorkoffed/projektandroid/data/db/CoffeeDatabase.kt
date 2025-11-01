package com.victorkoffed.projektandroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Huvuddatabas-klassen för appen.
 * Definierar alla entities (tabeller), vyer och konverterare.
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

        /**
         * Returnerar den singleton-instansen av databasen.
         * Bygger instansen om den inte redan existerar, synkroniserat.
         */
        fun getInstance(context: Context): CoffeeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CoffeeDatabase::class.java,
                    "coffee_journal.db"
                )
                    .addCallback(DatabaseCallback)
                    // För att undvika krasch i produktion MÅSTE man lägga till en Migration
                    // för varje versionshopp (t.ex. 4 till 5, 5 till 6, etc.)
                    .addMigrations(
                        Migrations.MIGRATION_4_5
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Callback för att köra initial SQL, t.ex. PRAGMA, skapa Vyer, och för-populera.
         */
        private val DatabaseCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("PRAGMA foreign_keys = ON;")

                // För-populera med vanliga bryggmetoder
                db.execSQL("INSERT INTO Method (name) VALUES ('V60');")
                db.execSQL("INSERT INTO Method (name) VALUES ('Aeropress');")
            }
        }
    }
}

// --- Migrations-klass för att demonstrera/förbereda ---
// För framtida versioner: Implementera en Migration för varje versionshopp.
object Migrations {
    /**
     * Exempel på en migration från version 4 till version 5.
     * Denna MÅSTE implementeras med faktisk SQL om du ändrar schemat.
     */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // db.execSQL("ALTER TABLE 'Bean' ADD COLUMN 'new_column' INTEGER NOT NULL DEFAULT 0")
        }
    }
}