package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import com.example.ui.auth.SignInScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutUser
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRootContent(activity = this)
                }
            }
        }
    }
}

@Composable
fun AppRootContent(activity: ComponentActivity) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(FirebaseAuth.getInstance().currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        onDispose {
            FirebaseAuth.getInstance().removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = FirebaseAuth.getInstance().currentUser
                },
                onUnauthenticated = {
                    // Stay on SignInScreen
                },
                scope = coroutineScope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = {
                val authedUser = FirebaseAuth.getInstance().currentUser
                currentUser = authedUser
                if (authedUser != null) {
                    val dbId = context.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(dbId)
                    val repo = ChatRepository(db)
                    val profile = UserProfile(
                        userId = authedUser.uid,
                        email = authedUser.email,
                        displayName = authedUser.displayName,
                        photoUrl = authedUser.photoUrl?.toString()
                    )
                    coroutineScope.launch {
                        repo.syncUserProfile(profile)
                    }
                }
            }
        )
    } else {
        val chatViewModel: ChatViewModel = viewModel(
            key = user.uid,
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val resolvedDbId = context.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(resolvedDbId)
                    return ChatViewModel(
                        repository = ChatRepository(db),
                        currentUserId = user.uid
                    ) as T
                }
            }
        )

        ChatScreen(
            viewModel = chatViewModel,
            onSignOut = {
                signOutUser(
                    context = context,
                    credentialManager = credentialManager,
                    onSignOutComplete = {
                        currentUser = null
                    },
                    scope = coroutineScope
                )
            }
        )
    }
}
