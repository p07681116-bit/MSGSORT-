package com.example

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.billing.BillingConnectionState
import com.example.billing.BillingFlowLaunchResult
import com.example.billing.ProductCatalogState
import com.example.billing.PlayBillingManager
import com.example.data.repository.SortCodeRepository
import com.example.model.UserProfile
import com.example.ui.MainViewModel
import com.example.ui.components.DeletionUiState
import com.example.ui.components.MsgSortBottomNav
import com.example.ui.components.MsgSortTopBar
import com.example.ui.components.ScreenTab
import com.example.ui.screens.*
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MsgSortTheme
import com.example.util.AppLanguage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val repository = remember {
                val firestore = SortCodeRepository.getFirestoreInstance(context)
                SortCodeRepository(firestore)
            }
            val viewModel: MainViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        MainViewModel(repository)
                    }
                }
            )
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            MsgSortTheme(darkTheme = isDark) {
                MsgSortApp(
                    viewModel = viewModel,
                    isDarkTheme = isDark,
                    themeMode = themeMode
                )
            }
        }
    }
}

@Composable
fun MsgSortApp(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    themeMode: AppThemeMode
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = remember { Firebase.auth }
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    val repository = remember {
        val firestore = SortCodeRepository.getFirestoreInstance(context)
        SortCodeRepository(firestore)
    }

    // Google Play Billing Lifecycle Manager
    val activity = context as? Activity
    val playBillingManager = remember {
        PlayBillingManager(
            context = context,
            coroutineScope = coroutineScope,
            onPurchaseSuccess = { purchase, productId ->
                currentUser?.uid?.let { uid ->
                    viewModel.registerGooglePlayPurchase(uid, purchase, productId)
                    Toast.makeText(context, "Premium subscription activated via Google Play!", Toast.LENGTH_LONG).show()
                }
            },
            onBillingError = { errorMsg ->
                Log.w("MsgSortApp", "Billing notification: $errorMsg")
            }
        )
    }

    DisposableEffect(playBillingManager) {
        onDispose {
            playBillingManager.destroy()
        }
    }

    // Listen to Auth State
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            currentUser = user
            if (user != null) {
                viewModel.observeUserCodes(user.uid)
                viewModel.observeUserSubscription(user.uid)
                // Save user profile to Firestore
                coroutineScope.launch {
                    repository.saveUserProfile(
                        UserProfile(
                            userId = user.uid,
                            displayName = user.displayName ?: "User",
                            email = user.email ?: ""
                        )
                    )
                }
            } else {
                viewModel.onUserSignedOut()
            }
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // Startup silent sign-in attempt
    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(context, coroutineScope)
        }
    }

    // UI state from ViewModel
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val userCodes by viewModel.userCodes.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    val createdItem by viewModel.createdItem.collectAsStateWithLifecycle()
    val creationError by viewModel.creationError.collectAsStateWithLifecycle()
    val isRetrieving by viewModel.isRetrieving.collectAsStateWithLifecycle()
    val retrievedItem by viewModel.retrievedItem.collectAsStateWithLifecycle()
    val retrievalError by viewModel.retrievalError.collectAsStateWithLifecycle()
    val initialRetrievalCode by viewModel.initialRetrievalCode.collectAsStateWithLifecycle()
    val initialCreateContentType by viewModel.initialCreateContentType.collectAsStateWithLifecycle()
    val userSubscription by viewModel.userSubscription.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val isPurchasing by viewModel.isPurchasing.collectAsStateWithLifecycle()
    val billingConnectionState by playBillingManager.connectionState.collectAsStateWithLifecycle()
    val productCatalogState by playBillingManager.productCatalogState.collectAsStateWithLifecycle()
    val formattedWeeklyPrice by playBillingManager.formattedWeeklyPrice.collectAsStateWithLifecycle()
    val formattedMonthlyPrice by playBillingManager.formattedMonthlyPrice.collectAsStateWithLifecycle()
    val formattedAnnualPrice by playBillingManager.formattedAnnualPrice.collectAsStateWithLifecycle()

    // Handle back button when not on Home tab
    if (currentTab != ScreenTab.HOME) {
        BackHandler {
            viewModel.selectTab(ScreenTab.HOME)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentTab != ScreenTab.HOME && currentTab != ScreenTab.PREMIUM && currentTab != ScreenTab.SCAN) {
                MsgSortTopBar(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { viewModel.selectLanguage(it) },
                    userEmail = currentUser?.email
                )
            }
        },
        bottomBar = {
            if (currentTab != ScreenTab.PREMIUM && currentTab != ScreenTab.SCAN) {
                MsgSortBottomNav(
                    selectedTab = currentTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    language = currentLanguage
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ScreenTab.HOME -> {
                    HomeScreen(
                        language = currentLanguage,
                        recentCodes = userCodes,
                        isPremium = isPremium,
                        currentUser = currentUser,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { viewModel.toggleTheme() },
                        onNavigateTab = { viewModel.selectTab(it) },
                        onCodeSelected = { code ->
                            viewModel.navigateToRetrieveWithCode(code)
                        },
                        onQuickAction = { type ->
                            viewModel.navigateToCreateWithType(type)
                        },
                        onLanguageSelected = { viewModel.selectLanguage(it) },
                        modifier = Modifier.statusBarsPadding()
                    )
                }

                ScreenTab.SCAN -> {
                    ScanScreen(
                        language = currentLanguage,
                        onCodeScanned = { code ->
                            viewModel.navigateToRetrieveWithCode(code)
                        },
                        onNavigateBack = {
                            viewModel.selectTab(ScreenTab.HOME)
                        }
                    )
                }

                ScreenTab.CREATE -> {
                    CreateSortCodeScreen(
                        language = currentLanguage,
                        currentUserId = currentUser?.uid,
                        isPremium = isPremium,
                        isCreating = isCreating,
                        createdItem = createdItem,
                        creationError = creationError,
                        onResetCreationError = {
                            viewModel.resetCreationError()
                        },
                        onCreateSortCode = { item, bytes ->
                            viewModel.createSortCode(item, bytes)
                        },
                        onResetCreatedItem = {
                            viewModel.resetCreatedItem()
                        },
                        onSignInClicked = {
                            onGoogleSignInClicked(context, coroutineScope)
                        },
                        onUpgradeToPremiumClicked = {
                            viewModel.selectTab(ScreenTab.PREMIUM)
                        },
                        initialContentType = initialCreateContentType
                    )
                }

                ScreenTab.RETRIEVE -> {
                    RetrieveScreen(
                        language = currentLanguage,
                        initialCode = initialRetrievalCode,
                        isRetrieving = isRetrieving,
                        retrievedItem = retrievedItem,
                        retrievalError = retrievalError,
                        onRetrieveCode = { code ->
                            viewModel.retrieveSortCode(code)
                        },
                        onResetRetrieval = {
                            viewModel.resetRetrieval()
                        },
                        onDownloadMediaBytes = { item ->
                            viewModel.getMediaBytesForSortCode(item)
                        }
                    )
                }

                ScreenTab.MY_CODES -> {
                    MySortCodesScreen(
                        language = currentLanguage,
                        userCodes = userCodes,
                        onSelectCode = { code ->
                            viewModel.navigateToRetrieveWithCode(code)
                        },
                        onRevokeCode = { code ->
                            viewModel.revokeSortCode(code)
                        },
                        onDeleteCode = { code ->
                            viewModel.deleteSortCode(code)
                        },
                        onCleanupExpired = {
                            currentUser?.uid?.let { uid ->
                                viewModel.cleanupExpiredCodes(uid) { count ->
                                    Toast.makeText(context, if (count > 0) "Cleaned up $count expired/revoked item(s)" else "No expired items to clean up", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onNavigateCreate = {
                            viewModel.selectTab(ScreenTab.CREATE)
                        }
                    )
                }

                ScreenTab.PROFILE -> {
                    var deletionState by remember { mutableStateOf<DeletionUiState>(DeletionUiState.Idle) }

                    fun executeAccountDeletion() {
                        val user = currentUser
                        if (user == null) {
                            deletionState = DeletionUiState.Error("You must be signed in to delete an account.")
                            return
                        }

                        coroutineScope.launch {
                            try {
                                // 1. Require recent authentication check
                                val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
                                val isSessionFresh = (System.currentTimeMillis() - lastSignIn) < (5 * 60 * 1000L) // 5 minutes
                                if (!isSessionFresh) {
                                    deletionState = DeletionUiState.ReauthRequired(
                                        "For security, Firebase requires recent authentication before deleting an account. Please click below to verify your Google identity."
                                    )
                                    return@launch
                                }

                                deletionState = DeletionUiState.InProgress("Purging your SortCodes and Storage files...")

                                // 2. Delete all user's SortCodes and Firebase Storage assets
                                val dataResult = repository.deleteUserAccountData(user.uid) { step ->
                                    deletionState = DeletionUiState.InProgress(step)
                                }

                                if (dataResult.isFailure) {
                                    val ex = dataResult.exceptionOrNull()
                                    deletionState = DeletionUiState.Error(
                                        "Failed to purge personal records: ${ex?.localizedMessage ?: "Unknown error"}"
                                    )
                                    return@launch
                                }

                                // 3. Delete Firebase Authentication Account
                                deletionState = DeletionUiState.InProgress("Permanently deleting authentication account...")
                                try {
                                    user.delete().await()
                                } catch (e: FirebaseAuthRecentLoginRequiredException) {
                                    deletionState = DeletionUiState.ReauthRequired(
                                        "Recent sign-in required by Firebase. Please click below to verify your Google identity."
                                    )
                                    return@launch
                                }

                                // 4. Clear local credentials & sign out
                                deletionState = DeletionUiState.InProgress("Finalizing sign-out...")
                                signOut(context, coroutineScope)
                                viewModel.selectTab(ScreenTab.HOME)
                                deletionState = DeletionUiState.Idle
                                Toast.makeText(
                                    context,
                                    "Account and associated data permanently deleted.",
                                    Toast.LENGTH_LONG
                                ).show()

                            } catch (e: Exception) {
                                deletionState = DeletionUiState.Error("Account deletion failed: ${e.localizedMessage}")
                            }
                        }
                    }

                    ProfileSettingsScreen(
                        language = currentLanguage,
                        currentUser = currentUser,
                        isPremium = isPremium,
                        themeMode = themeMode,
                        deletionState = deletionState,
                        onThemeModeSelected = { viewModel.setThemeMode(it) },
                        onLanguageSelected = { viewModel.selectLanguage(it) },
                        onSignInClicked = {
                            onGoogleSignInClicked(context, coroutineScope)
                        },
                        onSignOutClicked = {
                            signOut(context, coroutineScope)
                        },
                        onDeleteAccount = {
                            executeAccountDeletion()
                        },
                        onConfirmDeleteAccount = {
                            executeAccountDeletion()
                        },
                        onRequestReauth = {
                            onGoogleSignInClicked(context, coroutineScope)
                        },
                        onNavigateToPremium = {
                            viewModel.selectTab(ScreenTab.PREMIUM)
                        }
                    )
                }

                ScreenTab.PREMIUM -> {
                    PremiumScreen(
                        language = currentLanguage,
                        currentUserId = currentUser?.uid,
                        subscription = userSubscription,
                        isPurchasing = isPurchasing,
                        billingConnectionState = billingConnectionState,
                        productCatalogState = productCatalogState,
                        formattedWeeklyPrice = formattedWeeklyPrice,
                        formattedMonthlyPrice = formattedMonthlyPrice,
                        formattedAnnualPrice = formattedAnnualPrice,
                        onRefreshBilling = {
                            playBillingManager.refresh()
                        },
                        onSubscribePlan = { plan ->
                            currentUser?.uid?.let { _ ->
                                if (activity == null) {
                                    Toast.makeText(context, "Activity is not available to start Google Play checkout", Toast.LENGTH_SHORT).show()
                                    return@let
                                }
                                val result = playBillingManager.launchPurchaseFlow(activity, plan)
                                when (result) {
                                    is BillingFlowLaunchResult.Success -> {
                                        // Google Play billing sheet successfully opened
                                    }
                                    is BillingFlowLaunchResult.Error -> {
                                        Toast.makeText(
                                            context,
                                            result.userFriendlyMessage,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } ?: Toast.makeText(context, "Please sign in with Google first", Toast.LENGTH_SHORT).show()
                        },
                        onRestorePurchases = {
                            currentUser?.uid?.let { uid ->
                                playBillingManager.queryActivePurchases { activePurchases, _ ->
                                    if (activePurchases.isEmpty()) {
                                        viewModel.restorePurchases(uid) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Restored ${activePurchases.size} active Google Play purchase(s)!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } ?: Toast.makeText(context, "Please sign in with Google first", Toast.LENGTH_SHORT).show()
                        },
                        onBilldeskPayment = { paymentId, orderId, signature, plan ->
                            val uid = currentUser?.uid
                            if (uid == null) {
                                Toast.makeText(context, "Please sign in with Google first", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.verifyAndActivateBilldeskPayment(
                                    userId = uid,
                                    paymentId = paymentId,
                                    orderId = orderId,
                                    signature = signature,
                                    planId = plan.id,
                                    onSuccess = { sub ->
                                        Toast.makeText(
                                            context,
                                            "🎉 Premium activated via BillDesk for 7 days! Valid until: ${sub.formattedExpiryDate()}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        onRazorpayPayment = { paymentId, orderId, signature, plan ->
                            val uid = currentUser?.uid
                            if (uid == null) {
                                Toast.makeText(context, "Please sign in with Google first", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.verifyAndActivateRazorpayPayment(
                                    userId = uid,
                                    paymentId = paymentId,
                                    orderId = orderId,
                                    signature = signature,
                                    planId = plan.id,
                                    onSuccess = { sub ->
                                        Toast.makeText(
                                            context,
                                            "🎉 Premium activated for 7 days! Valid until: ${sub.formattedExpiryDate()}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        onNavigateBack = {
                            viewModel.selectTab(ScreenTab.PROFILE)
                        }
                    )
                }
            }
        }
    }
}

private const val TAG = "Auth"

private fun getWebClientId(context: Context): String? {
    return try {
        context.getString(R.string.default_web_client_id).trim().ifEmpty { null }
    } catch (e: Exception) {
        null
    }
}

fun attemptAutoSignIn(
    context: Context,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) return

    val clientId = getWebClientId(context) ?: run {
        Log.d(TAG, "default_web_client_id not configured in google-services.json")
        return
    }

    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto-sign-in skipped: ${e.localizedMessage}")
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    scope: CoroutineScope
) {
    val clientId = getWebClientId(context)
    if (clientId == null) {
        Toast.makeText(
            context,
            "Google Sign-In: Web Client ID missing in Firebase Console. Add SHA-1 and re-download google-services.json.",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    val credentialManager = CredentialManager.create(context)
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activity = context as? Activity ?: run {
                Log.e(TAG, "Context is not an Activity")
                return@launch
            }
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Unexpected credential type", Toast.LENGTH_SHORT).show()
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Toast.makeText(context, "Sign in failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun signOut(
    context: Context,
    scope: CoroutineScope
) {
    Firebase.auth.signOut()
    val credentialManager = CredentialManager.create(context)
    scope.launch {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credential state", e)
        }
    }
}
