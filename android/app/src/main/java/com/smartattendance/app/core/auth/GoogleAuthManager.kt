package com.smartattendance.app.core.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val googleId: String,
    val photoUrl: String? = null,
    val idToken: String? = null
)

object GoogleAuthManager {
    const val IIIT_DOMAIN = "iiitnr.edu.in"
    const val STUDENT_DOMAIN = "student.iiitnr.edu.in"
    const val DEMO_DOMAIN = "iitdemo.edu"
    const val WEB_CLIENT_ID = "969110741767-fuuhi63q3054eq0plg2qi68opgjspsqs.apps.googleusercontent.com"

    /**
     * Creates GoogleSignInClient configured for sign-in (any Google account / Gmail permitted).
     */
    fun getGoogleSignInClient(
        context: Context,
        serverClientId: String? = null,
        enforceHostedDomain: Boolean = false
    ): GoogleSignInClient {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()

        if (enforceHostedDomain) {
            builder.setHostedDomain(IIIT_DOMAIN)
        }

        val targetClient = if (!serverClientId.isNullOrBlank()) serverClientId else WEB_CLIENT_ID
        try {
            builder.requestIdToken(targetClient)
        } catch (_: Exception) {}

        return GoogleSignIn.getClient(context, builder.build())
    }

    /**
     * Checks if email is valid (allows all domains including Gmail, Yahoo, institutional, etc.).
     */
    fun isInstitutionalDomain(email: String): Boolean {
        val clean = email.trim().lowercase()
        return clean.isNotBlank() && clean.contains("@") && clean.contains(".")
    }

    /**
     * Extracts and validates the GoogleSignInAccount from activity intent.
     * Allows any Google account / Gmail to sign in.
     */
    fun parseSignInResult(data: Intent?, allowAnyDomain: Boolean = true): Result<GoogleUserProfile> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val email = account.email ?: ""

            if (!allowAnyDomain && !isInstitutionalDomain(email)) {
                return Result.failure(
                    SecurityException(
                        "Please sign in with a valid email account."
                    )
                )
            }

            val profile = GoogleUserProfile(
                email = email.lowercase().trim(),
                displayName = account.displayName ?: "Student",
                googleId = account.id ?: account.email ?: "",
                photoUrl = account.photoUrl?.toString(),
                idToken = account.idToken
            )

            Result.success(profile)
        } catch (e: ApiException) {
            val friendlyMsg = when (e.statusCode) {
                12500 -> "Google Sign-In configuration error (12500). Please ensure Google Play Services is updated."
                12501 -> "Google Sign-In was cancelled by user."
                10 -> "Google Play Services Developer Error (10). SHA-1 fingerprint registration pending in Google Cloud Console."
                else -> "Google Sign-In failed (${e.statusCode}): ${e.message}"
            }
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
