package com.victorkoffed.projektandroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Huvuddatabas-klassen för appen.
 * Definierar alla entities (tabeller) och vyer.
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
    // --- ÄNDRING 1: Versionen har ökats ---
    version = 3,
    // --- SLUT ÄNDRING 1 ---
    exportSchema = false // Kan sättas till true för produktionsappar
)
@TypeConverters(Converters::class) // Används för att konvertera t.ex. Date till Long (Epoch)
abstract class CoffeeDatabase : RoomDatabase() {

    abstract fun coffeeDao(): CoffeeDao

    companion object {
        @Volatile
        private var INSTANCE: CoffeeDatabase? = null

        // Byt namn till getInstance för att matcha CoffeeJournalApplication
        fun getInstance(context: Context): CoffeeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CoffeeDatabase::class.java,
                    "coffee_journal.db" // Byt namn för konsekvens
                )
                    .addCallback(DatabaseCallback) // Lägger till våra Triggers
                    // --- ÄNDRING 2: Lade till fallback ---
                    .fallbackToDestructiveMigration(false) // Använd destructive migration vid versionsökning
                    // --- SLUT ÄNDRING 2 ---
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Callback för att lägga till data och triggers när databasen skapas.
         */
        private val DatabaseCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Kör all din PRAGMA och TRIGGER SQL här
                db.execSQL("PRAGMA foreign_keys = ON;")

                // För-populera med V60
                db.execSQL("INSERT INTO Method (name) VALUES ('V60');")
                db.execSQL("INSERT INTO Method (name) VALUES ('Aeropress');") // Passar på att lägga till en till

                // Trigger för att minska lager vid ny bryggning OCH Trigger för att återställa lager vid raderad bryggning
                // BÅDA ÄR BORTTAGNA OCH HANTERAS NU VIA KOTLIN-KOD I CoffeeDao.kt för att säkerställa Flow-uppdateringar.
            }
        }
    }
}