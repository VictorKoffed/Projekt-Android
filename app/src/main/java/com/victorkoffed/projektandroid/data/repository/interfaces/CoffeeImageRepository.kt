package com.victorkoffed.projektandroid.data.repository.interfaces

/**
 * Interface för att hämta slumpmässiga kaffebilder från ett externt API.
 */
interface CoffeeImageRepository {
    /**
     * Hämtar URL:en till en slumpmässig kaffebild.
     * @return URL:en till bilden som String, eller null om anropet misslyckades.
     */
    suspend fun fetchRandomCoffeeImageUrl(): String?
}