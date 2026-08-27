package com.yourteam.smartcare.data.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Profile
import com.yourteam.smartcare.data.model.Relationship
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.tasks.await

object AuthRepository {

    // 1. Fetch and save FCM Token for Push Notifications
    suspend fun fetchAndSaveFCMToken(userId: String) {
        try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            SupabaseClient.client.from("profiles").update(
                {
                    set("fcm_token", fcmToken)
                }
            ) {
                filter { eq("id", userId) }
            }
            println("FCM Token saved successfully: $fcmToken")
        } catch (e: Exception) {
            println("Failed to fetch or save FCM token: ${e.message}")
        }
    }

    // 2. Elder Login (Uses PIN securely)
    suspend fun loginElder(phone: String, pin: String): Result<Unit> {
        return try {
            val dummyEmail = "$phone@smartcare.app"

            SupabaseClient.client.auth.signInWith(Email) {
                this.email = dummyEmail
                this.password = pin // Using the PIN as the secure password
            }

            // Optionally save the Elder's device token too
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            if (userId != null) {
                fetchAndSaveFCMToken(userId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3. Guardian Login
    suspend fun loginGuardian(email: String, password: String): Result<Unit> {
        return try {
            SupabaseClient.client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // Grab token so notifications go to this device
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
            if (userId != null) {
                fetchAndSaveFCMToken(userId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. Guardian Sign Up
    suspend fun signUpGuardian(
        email: String,
        password: String,
        name: String,
        phone: String
    ): Result<Unit> {
        return try {
            SupabaseClient.client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
                ?: throw Exception("Failed to retrieve Guardian ID")

            val guardianProfile = Profile(
                id = userId,
                name = name,
                role = "guardian",
                phone = phone,
                pairCode = null, // Fixed Kotlin naming
                fcmToken = null  // Added since we updated the model
            )
            SupabaseClient.client.from("profiles").insert(guardianProfile)

            // Save token right after successful signup
            fetchAndSaveFCMToken(userId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 5. Register and Link Elder (Done by Guardian)
    suspend fun registerAndLinkElder(
        name: String,
        phone: String
    ): Result<UserInfo> {
        return try {
            val guardianId = SupabaseClient.client.auth.currentUserOrNull()?.id
                ?: throw Exception("Guardian session missing. Please log in again.")

            val dummyEmail = "$phone@smartcare.app"
            val generatedPairCode = (100000..999999).random().toString() // 6-digit PIN

            val user = SupabaseClient.client.auth.signUpWith(Email) {
                this.email = dummyEmail
                this.password = generatedPairCode // PIN acts as the actual password
            }
            val userInfo = user ?: throw Exception("Elder account creation failed")

            val elderProfile = Profile(
                id = userInfo.id,
                name = name,
                role = "elder",
                phone = phone,
                pairCode = generatedPairCode, // Fixed Kotlin naming
                fcmToken = null
            )
            SupabaseClient.client.from("profiles").insert(elderProfile)

            val relationship = Relationship(
                guardianId = guardianId, // Fixed Kotlin naming
                elderId = userInfo.id    // Fixed Kotlin naming
            )
            SupabaseClient.client.from("relationships").insert(relationship)

            Result.success(userInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}