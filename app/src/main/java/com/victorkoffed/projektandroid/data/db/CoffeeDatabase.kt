package com.victorkoffed.projektandroid.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    // Databasversion. Måste ökas vid schemaändringar (t.ex. ny tabell eller kolumn).
    version = 3,
    // exportSchema bör vara 'false' i utveckling för att undvika varningar
    exportSchema = false
)
// TypeConverters används för att hantera icke-primitiva typer (som Date) i databasen.
@TypeConverters(Converters::class)
abstract class CoffeeDatabase : RoomDatabase() {

    abstract fun coffeeDao(): CoffeeDao

    companion object {
        // Håller den singleton-instansen av databasen, garanterar trådsäkerhet.
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
                    // Lägger till callback för att sätta upp triggers/vyer och initial data.
                    .addCallback(DatabaseCallback)
                    // Tillåter destruktiv migrering. Detta raderar befintlig data vid versionsökning,
                    // vilket är vanligt i utveckling men bör bytas ut mot riktiga migrationsstrategier i produktion.
                    .fallbackToDestructiveMigration()
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
                // Aktivera foreign key constraints för databasintegritet.
                db.execSQL("PRAGMA foreign_keys = ON;")

                // För-populera med vanliga bryggmetoder
                db.execSQL("INSERT INTO Method (name) VALUES ('V60');")
                db.execSQL("INSERT INTO Method (name) VALUES ('Aeropress');")

                // Notera: Logik för lagerhantering (decrement/increment) har flyttats
                // till @Transaction-metoder i CoffeeDao.kt. Detta görs för att säkerställa
                // att UI:et reaktivt uppdateras via Room Flows efter lagermodifiering.
            }
        }
    }
}