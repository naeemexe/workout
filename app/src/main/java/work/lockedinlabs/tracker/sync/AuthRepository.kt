package work.lockedinlabs.tracker.sync

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import work.lockedinlabs.tracker.R

/** Google-only sign-in: Android's account picker (Credential Manager) → Firebase Auth. */
class AuthRepository(private val auth: FirebaseAuth) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    val user: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** @param activityContext must be an Activity context so the account picker can show. */
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser> = runCatching {
        val token = googleIdToken(activityContext)
        plainErrors { auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await().user }
            ?: throw IllegalStateException(SIGN_IN_FAILED)
    }

    /** Asks you to pick your Google account again; Firebase wants a fresh sign-in before deleting an account. */
    suspend fun reauthenticate(activityContext: Context) {
        val user = auth.currentUser ?: error("Not signed in")
        val token = googleIdToken(activityContext)
        plainErrors { user.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await() }
    }

    /** Firebase's own error text is written for developers; people get a plain line and the log gets the detail. */
    private inline fun <T> plainErrors(block: () -> T): T = try {
        block()
    } catch (e: FirebaseNetworkException) {
        throw IllegalStateException("No connection. Try again.")
    } catch (e: FirebaseException) {
        Log.w(TAG, "Firebase sign-in failed", e)
        if ((e as? FirebaseAuthException)?.errorCode == "ERROR_USER_MISMATCH") {
            throw IllegalStateException("Pick the account you signed in with.")
        }
        throw IllegalStateException(SIGN_IN_FAILED)
    }

    /** Deletes the Firebase account itself (sign-in is gone; the cloud data must be deleted first). */
    suspend fun deleteUser() {
        auth.currentUser?.delete()?.await()
    }

    private suspend fun googleIdToken(activityContext: Context): String {
        val option = GetSignInWithGoogleOption.Builder(activityContext.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelled()
        } catch (e: NoCredentialException) {
            throw IllegalStateException("No Google account on this device. Add one in Settings → Accounts.")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google sign-in failed", e)
            throw IllegalStateException(SIGN_IN_FAILED)
        }
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            SIGN_IN_FAILED
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}

class SignInCancelled : Exception("Sign-in cancelled")

private const val TAG = "LockedInAuth"
private const val SIGN_IN_FAILED = "Couldn't sign in. Try again."
