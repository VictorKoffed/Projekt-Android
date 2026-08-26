package com.victorkoffed.projektandroid.data.repository.interfaces

/**
 * Defines the contract for retrieving external placeholder imagery.
 * Primarily utilized to provide visual fallback content when a user-provided
 * photo is absent for a brew session.
 */
interface CoffeeImageRepository {
    suspend fun fetchRandomCoffeeImageUrl(): String?
}