package com.yourteam.smartcare.data.repository

import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Profile
import com.yourteam.smartcare.data.model.Relationship
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from

object AuthRepository {

    suspend fun registerUser(
        emailInput: String,
        passwordInput: String,
        nameInput: String,
        phoneInput: String,
        roleInput: String
    ): Result<Profile> {
        return try {
            SupabaseClient.client.auth.signUpWith(Email) {
                email = emailInput.trim()
                password = passwordInput.trim()
            }

            val user = SupabaseClient.client.auth.currentUserOrNull()
                ?: return Result.failure(Exception("Registration failed: session not established."))

            val generatedPairCode = if (roleInput.lowercase() == "elder") {
                val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                (1..6).map { chars.random() }.joinToString("")
            } else null

            val newProfile = Profile(
                id = user.id,
                name = nameInput.trim(),
                role = roleInput.lowercase(),
                phone = phoneInput.trim(),
                pairCode = generatedPairCode
            )

            SupabaseClient.client.from("profiles").insert(newProfile)
            Result.success(newProfile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(emailInput: String, passwordInput: String): Result<Profile> {
        return try {
            SupabaseClient.client.auth.signInWith(Email) {
                email = emailInput.trim()
                password = passwordInput.trim()
            }

            val user = SupabaseClient.client.auth.currentUserOrNull()
                ?: return Result.failure(Exception("User not authenticated."))

            val profile = SupabaseClient.client.from("profiles")
                .select {
                    filter { eq("id", user.id) }
                }
                .decodeSingleOrNull<Profile>()

            if (profile != null) Result.success(profile)
            else Result.failure(Exception("Profile record not found in database."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun linkElder(identifier: String): Result<Profile> {
        return try {
            val guardian = SupabaseClient.client.auth.currentUserOrNull()
                ?: return Result.failure(Exception("Guardian is not logged in."))

            val elder = SupabaseClient.client.from("profiles")
                .select {
                    filter {
                        eq("role", "elder")
                        or {
                            eq("pair_code", identifier.trim().uppercase())
                            eq("phone", identifier.trim())
                        }
                    }
                }
                .decodeSingleOrNull<Profile>()
                ?: return Result.failure(Exception("No elder found with code/phone: $identifier"))

            val relationship = Relationship(
                guardianId = guardian.id,
                elderId = elder.id
            )
            SupabaseClient.client.from("relationships").insert(relationship)
            Result.success(elder)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}