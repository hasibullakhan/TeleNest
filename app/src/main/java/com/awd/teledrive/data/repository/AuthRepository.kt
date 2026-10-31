package com.awd.teledrive.data.repository

import android.content.Context
import android.util.Log
import com.awd.teledrive.core.Config
import com.awd.teledrive.core.utils.VersionUtils
import com.awd.teledrive.data.remote.TelegramClient
import com.awd.teledrive.data.secure.SecureSettings
import com.awd.teledrive.di.AccountDatabaseManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TelegramUserProfile(
    val id: Long = 0L,
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val bio: String = "",
    val isPremium: Boolean = false,
    val avatarPath: String? = null,
    val slotId: String = "default",
    val lastActiveAt: Long = 0L
) {
    val fullName: String
        get() = listOf(firstName.trim(), lastName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { if (phoneNumber.isNotBlank()) formattedPhone else "Telegram User" }

    val formattedPhone: String
        get() = if (phoneNumber.isNotBlank()) {
            if (phoneNumber.startsWith("+")) phoneNumber else "+$phoneNumber"
        } else ""
}

@Singleton
class AuthRepository @Inject constructor(
    private val telegramClient: TelegramClient,
    private val secureSettings: SecureSettings,
    private val settingsRepository: SettingsRepository,
    private val accountDatabaseManager: AccountDatabaseManager,
    @param:ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accountsPrefs = context.getSharedPreferences("teledrive_accounts_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    private val _userProfile = MutableStateFlow<TelegramUserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<TelegramUserProfile>>(emptyList())
    val savedAccounts: StateFlow<List<TelegramUserProfile>> = _savedAccounts.asStateFlow()

    private val _activeSlotId = MutableStateFlow("default")
    val activeSlotId: StateFlow<String> = _activeSlotId.asStateFlow()

    private val _isAddingNewAccount = MutableStateFlow(false)
    val isAddingNewAccount: StateFlow<Boolean> = _isAddingNewAccount.asStateFlow()

    private var previousSlotBeforeAdd: String? = null
    private val trackedAvatarBySlot = ConcurrentHashMap<String, Int>()
    private val tdlibParamsSentBySlot = ConcurrentHashMap<String, Pair<Int, String>>()
    private val pendingPhoneBySlot = ConcurrentHashMap<String, String>()
    private val phoneBeingSentBySlot = ConcurrentHashMap<String, String>()
    private val codeSubmissionInFlightBySlot = ConcurrentHashMap.newKeySet<String>()
    private val passwordSubmissionInFlightBySlot = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val KEY_SAVED_ACCOUNTS = "saved_accounts_json"
        private const val KEY_ACTIVE_SLOT = "active_account_slot"
    }

    fun getApiId(slotId: String = _activeSlotId.value): Int {
        val cleanSlot = slotId.ifBlank { "default" }
        val slotStored = secureSettings.getInt("api_id_$cleanSlot", 0)
        if (slotStored != 0) return slotStored
        if (cleanSlot == "default") {
            val legacyStored = secureSettings.getInt("api_id", 0)
            if (legacyStored != 0) return legacyStored
            if (secureSettings.getBoolean("api_explicitly_cleared", false)) return 0
            return Config.API_ID
        }
        return 0
    }

    fun getApiHash(slotId: String = _activeSlotId.value): String {
        val cleanSlot = slotId.ifBlank { "default" }
        val slotStored = secureSettings.getString("api_hash_$cleanSlot", "")
        if (!slotStored.isNullOrEmpty()) return slotStored
        if (cleanSlot == "default") {
            val legacyStored = secureSettings.getString("api_hash", "")
            if (!legacyStored.isNullOrEmpty()) return legacyStored
            if (secureSettings.getBoolean("api_explicitly_cleared", false)) return ""
            return Config.API_HASH
        }
        return ""
    }

    init {
        loadSavedAccountsFromPrefs()
        val initialSlot = accountsPrefs.getString(KEY_ACTIVE_SLOT, "default")?.takeIf { it.isNotBlank() } ?: "default"
        _activeSlotId.value = initialSlot
        accountDatabaseManager.switchAccountSlot(initialSlot)
        telegramClient.switchSlot(initialSlot)
        _userProfile.value = _savedAccounts.value.find { it.slotId == initialSlot }

        scope.launch {
            telegramClient.slotUpdates.collect { slotUpdate ->
                val slot = slotUpdate.slotId
                val update = slotUpdate.update
                when (update) {
                    is TdApi.UpdateAuthorizationState -> {
                        if (slot == _activeSlotId.value) {
                            _authState.value = update.authorizationState
                        }
                        handleAuthorizationStateForSlot(slot, update.authorizationState)
                    }
                    is TdApi.UpdateFile -> {
                        val file = update.file
                        val trackedId = trackedAvatarBySlot[slot] ?: 0
                        if (trackedId != 0 && file.id == trackedId && !file.local.path.isNullOrEmpty() && File(file.local.path).exists()) {
                            val persistedPath = persistAvatarFile(slot, file.local.path)
                            updateSavedAccountAvatar(slot, persistedPath)
                            if (slot == _activeSlotId.value) {
                                _userProfile.value = _userProfile.value?.copy(avatarPath = persistedPath)
                            }
                        }
                    }
                }
            }
        }

        telegramClient.sendToSlot(initialSlot, TdApi.GetAuthorizationState()) { result ->
            if (result is TdApi.AuthorizationState && _activeSlotId.value == initialSlot) {
                _authState.value = result
                handleAuthorizationStateForSlot(initialSlot, result)
            }
        }
    }

    private fun loadSavedAccountsFromPrefs() {
        try {
            val raw = accountsPrefs.getString(KEY_SAVED_ACCOUNTS, null)
            if (!raw.isNullOrBlank()) {
                val decoded = json.decodeFromString<List<TelegramUserProfile>>(raw)
                _savedAccounts.value = decoded.distinctBy { if (it.id != 0L) "id_${it.id}" else "slot_${it.slotId}" }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to load saved accounts: ${e.message}")
        }
    }

    private fun saveAccountsToPrefs(accounts: List<TelegramUserProfile>) {
        try {
            val clean = accounts
                .filter { it.id != 0L || it.phoneNumber.isNotBlank() || it.firstName.isNotBlank() }
                .distinctBy { if (it.id != 0L) "id_${it.id}" else "slot_${it.slotId}" }
            _savedAccounts.value = clean
            accountsPrefs.edit()
                .putString(KEY_SAVED_ACCOUNTS, json.encodeToString(clean))
                .apply()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to save accounts: ${e.message}")
        }
    }

    private fun persistAvatarFile(slotId: String, sourcePath: String): String {
        return try {
            val src = File(sourcePath)
            if (!src.exists()) return sourcePath
            val avatarsDir = File(context.filesDir, "account_avatars").apply { mkdirs() }
            val dest = File(avatarsDir, "avatar_${slotId}.jpg")
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (_: Exception) {
            sourcePath
        }
    }

    private fun updateSavedAccountAvatar(slotId: String, avatarPath: String) {
        val current = _savedAccounts.value.toMutableList()
        val idx = current.indexOfFirst { it.slotId == slotId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(avatarPath = avatarPath)
            saveAccountsToPrefs(current)
        }
    }

    private fun upsertSavedAccount(profile: TelegramUserProfile) {
        val current = _savedAccounts.value.toMutableList()
        // Remove any existing entry with the same slotId or same non-zero Telegram user ID
        val existingForUser = current.find { it.slotId == profile.slotId || (profile.id != 0L && it.id == profile.id) }
        val mergedAvatar = profile.avatarPath ?: existingForUser?.avatarPath
        val mergedBio = profile.bio.ifBlank { existingForUser?.bio.orEmpty() }
        val finalProfile = profile.copy(
            avatarPath = mergedAvatar,
            bio = mergedBio,
            lastActiveAt = System.currentTimeMillis()
        )
        val existingIdx = current.indexOfFirst { it.slotId == profile.slotId }
        if (existingIdx >= 0) {
            current[existingIdx] = finalProfile
        } else {
            current.add(finalProfile)
        }
        // Deduplicate by Telegram user ID while keeping the current slotId if user logged into same account
        val deduplicated = current
            .sortedByDescending { if (it.slotId == profile.slotId) Long.MAX_VALUE else it.lastActiveAt }
            .distinctBy { if (it.id != 0L) "id_${it.id}" else "slot_${it.slotId}" }
        saveAccountsToPrefs(deduplicated)
    }

    fun fetchUserProfile(slotId: String = _activeSlotId.value) {
        telegramClient.sendToSlot(slotId, TdApi.GetMe()) { result ->
            if (result is TdApi.User) {
                val activeUsername = result.usernames?.activeUsernames?.firstOrNull().orEmpty()
                val photoFile = result.profilePhoto?.big ?: result.profilePhoto?.small
                var localAvatarPath = photoFile?.local?.path?.takeIf { it.isNotEmpty() && File(it).exists() }
                    ?.let { persistAvatarFile(slotId, it) }
                    ?: _savedAccounts.value.find { it.slotId == slotId }?.avatarPath?.takeIf { File(it).exists() }

                if (photoFile != null && photoFile.id != 0 && (localAvatarPath == null || !File(localAvatarPath).exists())) {
                    trackedAvatarBySlot[slotId] = photoFile.id
                    telegramClient.sendToSlot(slotId, TdApi.DownloadFile(photoFile.id, 1, 0, 0, true)) { dlResult ->
                        if (dlResult is TdApi.File && !dlResult.local.path.isNullOrEmpty() && File(dlResult.local.path).exists()) {
                            val persisted = persistAvatarFile(slotId, dlResult.local.path)
                            updateSavedAccountAvatar(slotId, persisted)
                            if (_activeSlotId.value == slotId) {
                                _userProfile.value = _userProfile.value?.copy(avatarPath = persisted)
                            }
                        }
                    }
                }

                val existingBio = _savedAccounts.value.find { it.slotId == slotId }?.bio
                    ?: (_userProfile.value?.takeIf { it.slotId == slotId }?.bio).orEmpty()

                val profile = TelegramUserProfile(
                    id = result.id,
                    firstName = result.firstName.orEmpty(),
                    lastName = result.lastName.orEmpty(),
                    username = activeUsername,
                    phoneNumber = result.phoneNumber.orEmpty(),
                    bio = existingBio,
                    isPremium = result.isPremium,
                    avatarPath = localAvatarPath,
                    slotId = slotId,
                    lastActiveAt = System.currentTimeMillis()
                )

                // Check if user logged into an account that is already saved in another slot!
                val duplicateSlotAccount = _savedAccounts.value.find {
                    it.id == result.id && it.slotId != slotId && result.id != 0L
                }
                if (duplicateSlotAccount != null && _isAddingNewAccount.value) {
                    // Automatically switch to the existing slot and clean up the duplicate temporary slot
                    val existingSlot = duplicateSlotAccount.slotId
                    cleanupSlotStorage(slotId)
                    switchToAccount(existingSlot)
                    return@sendToSlot
                }

                upsertSavedAccount(profile)
                if (_activeSlotId.value == slotId) {
                    _userProfile.value = profile
                    _isAddingNewAccount.value = false
                    previousSlotBeforeAdd = null
                }

                telegramClient.sendToSlot(slotId, TdApi.GetUserFullInfo(result.id)) { fullResult ->
                    if (fullResult is TdApi.UserFullInfo) {
                        val bioText = fullResult.bio?.text.orEmpty()
                        val updated = (_userProfile.value?.takeIf { it.slotId == slotId } ?: profile).copy(bio = bioText)
                        upsertSavedAccount(updated)
                        if (_activeSlotId.value == slotId) {
                            _userProfile.value = updated
                        }
                    }
                }
            }
        }
    }

    private fun getTdlibDirectoryForSlot(slotId: String): String {
        val clean = slotId.ifBlank { "default" }
        val dirName = if (clean == "default") "tdlib" else "tdlib_$clean"
        return File(context.filesDir, dirName).absolutePath
    }

    private fun handleAuthorizationStateForSlot(slotId: String, state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateReady -> {
                pendingPhoneBySlot.remove(slotId)
                phoneBeingSentBySlot.remove(slotId)
                codeSubmissionInFlightBySlot.remove(slotId)
                passwordSubmissionInFlightBySlot.remove(slotId)
                if (slotId == _activeSlotId.value) {
                    _authError.value = null
                    _isAddingNewAccount.value = false
                    previousSlotBeforeAdd = null
                }
                fetchUserProfile(slotId)
            }
            is TdApi.AuthorizationStateWaitCode -> {
                pendingPhoneBySlot.remove(slotId)
                phoneBeingSentBySlot.remove(slotId)
                codeSubmissionInFlightBySlot.remove(slotId)
                if (slotId == _activeSlotId.value) {
                    _authError.value = null
                }
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                pendingPhoneBySlot.remove(slotId)
                phoneBeingSentBySlot.remove(slotId)
                codeSubmissionInFlightBySlot.remove(slotId)
                passwordSubmissionInFlightBySlot.remove(slotId)
                if (slotId == _activeSlotId.value) {
                    _authError.value = null
                }
            }
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                phoneBeingSentBySlot.remove(slotId)
                val slotHasSavedAccount = _savedAccounts.value.any { it.slotId == slotId }
                val hasPendingPhone = pendingPhoneBySlot.containsKey(slotId)
                val shouldAutoSendParams = isApiConfigured(slotId) &&
                    !secureSettings.getBoolean("api_explicitly_cleared_$slotId", false) &&
                    (slotHasSavedAccount || hasPendingPhone)
                if (shouldAutoSendParams) {
                    sendTdlibParametersForSlot(slotId, getApiId(slotId), getApiHash(slotId))
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                val pendingPhone = pendingPhoneBySlot.remove(slotId)
                if (!pendingPhone.isNullOrBlank()) {
                    setPhoneNumberForSlot(slotId, pendingPhone)
                }
            }
            is TdApi.AuthorizationStateWaitRegistration -> {
                telegramClient.sendToSlot(slotId, TdApi.RegisterUser("TeleNest", "User", false))
            }
            is TdApi.AuthorizationStateClosed -> {
                tdlibParamsSentBySlot.remove(slotId)
                phoneBeingSentBySlot.remove(slotId)
                codeSubmissionInFlightBySlot.remove(slotId)
                passwordSubmissionInFlightBySlot.remove(slotId)
                if (slotId == _activeSlotId.value && !telegramClient.isRecreating(slotId)) {
                    telegramClient.recreateSlotClient(slotId) {
                        telegramClient.sendToSlot(slotId, TdApi.GetAuthorizationState()) { res ->
                            if (res is TdApi.AuthorizationState && _activeSlotId.value == slotId) {
                                _authState.value = res
                                handleAuthorizationStateForSlot(slotId, res)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isBenignTdlibError(msg: String): Boolean {
        return msg.contains("Unexpected setTdlibParameters", ignoreCase = true) ||
            msg.contains("Another authorization query has started", ignoreCase = true)
    }

    private fun sendTdlibParametersForSlot(
        slotId: String,
        apiId: Int,
        apiHash: String,
        onSuccess: (() -> Unit)? = null
    ) {
        val cleanSlot = slotId.ifBlank { "default" }
        val targetCreds = apiId to apiHash
        if (tdlibParamsSentBySlot[cleanSlot] == targetCreds) {
            onSuccess?.invoke()
            return
        }
        tdlibParamsSentBySlot[cleanSlot] = targetCreds

        val parameters = TdApi.SetTdlibParameters(
            false,
            getTdlibDirectoryForSlot(cleanSlot),
            "",
            null,
            true,
            true,
            true,
            false,
            apiId,
            apiHash,
            "en",
            android.os.Build.MODEL,
            android.os.Build.VERSION.RELEASE,
            VersionUtils.getVersionName(context)
        )

        telegramClient.sendToSlot(cleanSlot, parameters) { result ->
            if (result is TdApi.Error) {
                val msg = result.message.orEmpty()
                if (isBenignTdlibError(msg)) {
                    // TDLib already accepted parameters or is processing authorization; treat as benign success
                    if (cleanSlot == _activeSlotId.value) {
                        _authError.value = null
                    }
                    onSuccess?.invoke()
                } else if (msg.contains("Can't lock file", ignoreCase = true) || msg.contains("already in use", ignoreCase = true)) {
                    Log.w("AuthRepository", "Transient file lock on slot $cleanSlot, auto-retrying in 350ms...")
                    scope.launch {
                        kotlinx.coroutines.delay(350)
                        telegramClient.recreateSlotClient(cleanSlot) {
                            telegramClient.sendToSlot(cleanSlot, parameters) { retryResult ->
                                if (retryResult is TdApi.Error) {
                                    val retryMsg = retryResult.message.orEmpty()
                                    if (isBenignTdlibError(retryMsg)) {
                                        if (cleanSlot == _activeSlotId.value) _authError.value = null
                                        onSuccess?.invoke()
                                    } else {
                                        tdlibParamsSentBySlot.remove(cleanSlot)
                                        pendingPhoneBySlot.remove(cleanSlot)
                                        if (cleanSlot == _activeSlotId.value) {
                                            _authError.value = retryMsg
                                        }
                                    }
                                } else {
                                    if (cleanSlot == _activeSlotId.value) {
                                        _authError.value = null
                                        settingsRepository.applyCacheSettings()
                                    }
                                    onSuccess?.invoke()
                                }
                            }
                        }
                    }
                } else {
                    tdlibParamsSentBySlot.remove(cleanSlot)
                    pendingPhoneBySlot.remove(cleanSlot)
                    if (cleanSlot == _activeSlotId.value) {
                        _authError.value = msg
                    }
                }
            } else {
                if (cleanSlot == _activeSlotId.value) {
                    _authError.value = null
                    settingsRepository.applyCacheSettings()
                }
                onSuccess?.invoke()
            }
        }
    }

    fun switchToAccount(targetSlotId: String, onSwitched: (() -> Unit)? = null) {
        val cleanSlot = targetSlotId.ifBlank { "default" }
        _authError.value = null
        _isAddingNewAccount.value = false
        previousSlotBeforeAdd = null

        accountsPrefs.edit().putString(KEY_ACTIVE_SLOT, cleanSlot).apply()
        _activeSlotId.value = cleanSlot

        // Immediately update userProfile from savedAccounts so UI reflects the switched account in 0ms
        val cachedProfile = _savedAccounts.value.find { it.slotId == cleanSlot }
        if (cachedProfile != null) {
            _userProfile.value = cachedProfile
            upsertSavedAccount(cachedProfile)
        } else {
            _userProfile.value = null
        }

        // Switch underlying Room Database & TDLib Client
        accountDatabaseManager.switchAccountSlot(cleanSlot)
        telegramClient.switchSlot(cleanSlot)

        // Check TDLib authorization state for targetSlot
        telegramClient.sendToSlot(cleanSlot, TdApi.GetAuthorizationState()) { result ->
            if (result is TdApi.AuthorizationState && _activeSlotId.value == cleanSlot) {
                _authState.value = result
                handleAuthorizationStateForSlot(cleanSlot, result)
            }
        }
        onSwitched?.invoke()
    }

    fun startAddNewAccount() {
        val currentSlot = _activeSlotId.value
        if (!_isAddingNewAccount.value) {
            previousSlotBeforeAdd = currentSlot
        }
        val newSlotId = "acc_${System.currentTimeMillis()}"
        _authError.value = null
        _isAddingNewAccount.value = true
        _userProfile.value = null
        _activeSlotId.value = newSlotId
        accountsPrefs.edit().putString(KEY_ACTIVE_SLOT, newSlotId).apply()

        // Switch Database and TDLib client to the fresh slot without pre-filling old credentials
        accountDatabaseManager.switchAccountSlot(newSlotId)
        telegramClient.switchSlot(newSlotId)
        _authState.value = TdApi.AuthorizationStateWaitTdlibParameters()
    }

    fun cancelAddNewAccount(): Boolean {
        val fallbackSlot = previousSlotBeforeAdd
            ?.takeIf { slot -> _savedAccounts.value.any { it.slotId == slot } }
            ?: _savedAccounts.value.firstOrNull()?.slotId
            ?: return false

        val cancelledSlot = _activeSlotId.value
        _authError.value = null
        _isAddingNewAccount.value = false
        previousSlotBeforeAdd = null
        pendingPhoneBySlot.remove(cancelledSlot)
        phoneBeingSentBySlot.remove(cancelledSlot)
        tdlibParamsSentBySlot.remove(cancelledSlot)
        if (cancelledSlot != fallbackSlot && _savedAccounts.value.none { it.slotId == cancelledSlot }) {
            cleanupSlotStorage(cancelledSlot)
        }
        switchToAccount(fallbackSlot)
        return true
    }

    private fun cleanupSlotStorage(slotId: String) {
        val clean = slotId.ifBlank { "default" }
        tdlibParamsSentBySlot.remove(clean)
        pendingPhoneBySlot.remove(clean)
        phoneBeingSentBySlot.remove(clean)
        codeSubmissionInFlightBySlot.remove(clean)
        passwordSubmissionInFlightBySlot.remove(clean)
        telegramClient.removeSlotClient(clean)
        accountDatabaseManager.deleteAccountDatabase(clean)
        try {
            secureSettings.saveInt("api_id_$clean", 0)
            secureSettings.saveString("api_hash_$clean", "")
        } catch (_: Exception) {}
        try {
            val prefName = if (clean == "default") "teledrive_manifest_store" else "teledrive_manifest_store_$clean"
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (_: Exception) {}
        try {
            File(getTdlibDirectoryForSlot(clean)).deleteRecursively()
        } catch (_: Exception) {}
        try {
            File(context.filesDir, "account_avatars/avatar_${clean}.jpg").delete()
        } catch (_: Exception) {}
    }

    fun removeAccount(slotId: String) {
        val clean = slotId.ifBlank { "default" }
        val remaining = _savedAccounts.value.filter { it.slotId != clean }
        saveAccountsToPrefs(remaining)

        telegramClient.sendToSlot(clean, TdApi.LogOut()) {
            cleanupSlotStorage(clean)
        }
        cleanupSlotStorage(clean)

        if (_activeSlotId.value == clean) {
            val nextAccount = remaining.firstOrNull()
            if (nextAccount != null) {
                switchToAccount(nextAccount.slotId)
            } else {
                _userProfile.value = null
                _isAddingNewAccount.value = false
                _authError.value = null
                accountsPrefs.edit().putString(KEY_ACTIVE_SLOT, "default").apply()
                _activeSlotId.value = "default"
                tdlibParamsSentBySlot.remove("default")
                pendingPhoneBySlot.remove("default")
                phoneBeingSentBySlot.remove("default")
                secureSettings.saveInt("api_id_default", 0)
                secureSettings.saveString("api_hash_default", "")
                secureSettings.saveInt("api_id", 0)
                secureSettings.saveString("api_hash", "")
                secureSettings.saveBoolean("api_explicitly_cleared", true)
                accountDatabaseManager.switchAccountSlot("default")
                telegramClient.recreateSlotClient("default")
                telegramClient.switchSlot("default")
                _authState.value = TdApi.AuthorizationStateWaitTdlibParameters()
            }
        }
    }

    private fun saveCredentialsForSlot(slotId: String, apiId: Int, apiHash: String) {
        val cleanSlot = slotId.ifBlank { "default" }
        secureSettings.saveInt("api_id_$cleanSlot", apiId)
        secureSettings.saveString("api_hash_$cleanSlot", apiHash)
        secureSettings.saveBoolean("api_explicitly_cleared_$cleanSlot", false)
        if (cleanSlot == "default" || secureSettings.getInt("api_id", 0) == 0) {
            secureSettings.saveInt("api_id", apiId)
            secureSettings.saveString("api_hash", apiHash)
            secureSettings.saveBoolean("api_explicitly_cleared", false)
        }
    }

    fun saveApiCredentials(apiId: Int, apiHash: String) {
        _authError.value = null
        val currentSlot = _activeSlotId.value
        saveCredentialsForSlot(currentSlot, apiId, apiHash)

        telegramClient.sendToSlot(currentSlot, TdApi.GetAuthorizationState()) { state ->
            if (state is TdApi.AuthorizationStateWaitTdlibParameters) {
                sendTdlibParametersForSlot(currentSlot, apiId, apiHash)
            }
        }
    }

    fun submitCredentialsAndPhone(apiId: Int, apiHash: String, phoneNumber: String) {
        _authError.value = null
        val currentSlot = _activeSlotId.value
        val cleanHash = apiHash.trim()
        val cleanPhone = phoneNumber.trim()
        saveCredentialsForSlot(currentSlot, apiId, cleanHash)

        val previousSentCreds = tdlibParamsSentBySlot[currentSlot]
        val credsChanged = previousSentCreds != null && previousSentCreds != (apiId to cleanHash)

        telegramClient.sendToSlot(currentSlot, TdApi.GetAuthorizationState()) { state ->
            val isWaitParams = state is TdApi.AuthorizationStateWaitTdlibParameters
            val isWaitPhone = state is TdApi.AuthorizationStateWaitPhoneNumber

            if (credsChanged || (!isWaitParams && !isWaitPhone)) {
                // If credentials changed or client is not in a ready-for-parameters state, recreate client cleanly first.
                // Store cleanPhone in pendingPhoneBySlot so handleAuthorizationStateForSlot sends it ONCE when WaitPhoneNumber arrives.
                tdlibParamsSentBySlot.remove(currentSlot)
                phoneBeingSentBySlot.remove(currentSlot)
                pendingPhoneBySlot[currentSlot] = cleanPhone
                telegramClient.recreateSlotClient(currentSlot) {
                    sendTdlibParametersForSlot(currentSlot, apiId, cleanHash)
                }
                return@sendToSlot
            }

            if (isWaitPhone) {
                // TDLib already accepted parameters matching these credentials and is waiting for phone number right now
                pendingPhoneBySlot.remove(currentSlot)
                phoneBeingSentBySlot.remove(currentSlot)
                setPhoneNumberForSlot(currentSlot, cleanPhone)
            } else {
                // Currently in WaitTdlibParameters: store phone in pendingPhoneBySlot and send parameters.
                // Once TDLib transitions to AuthorizationStateWaitPhoneNumber, handleAuthorizationStateForSlot will send cleanPhone strictly once!
                phoneBeingSentBySlot.remove(currentSlot)
                pendingPhoneBySlot[currentSlot] = cleanPhone
                sendTdlibParametersForSlot(currentSlot, apiId, cleanHash)
            }
        }
    }

    fun clearApiCredentials() {
        _authError.value = null
        val currentSlot = _activeSlotId.value
        tdlibParamsSentBySlot.remove(currentSlot)
        pendingPhoneBySlot.remove(currentSlot)
        phoneBeingSentBySlot.remove(currentSlot)
        secureSettings.saveInt("api_id_$currentSlot", 0)
        secureSettings.saveString("api_hash_$currentSlot", "")
        secureSettings.saveBoolean("api_explicitly_cleared_$currentSlot", true)
        if (currentSlot == "default") {
            secureSettings.saveInt("api_id", 0)
            secureSettings.saveString("api_hash", "")
            secureSettings.saveBoolean("api_explicitly_cleared", true)
        }
        _authState.value = TdApi.AuthorizationStateWaitTdlibParameters()
        telegramClient.sendToSlot(currentSlot, TdApi.LogOut())
    }

    fun isApiConfigured(slotId: String = _activeSlotId.value): Boolean {
        return getApiId(slotId) != 0 && getApiHash(slotId).isNotEmpty()
    }

    private fun setPhoneNumberForSlot(slotId: String, phoneNumber: String) {
        val cleanSlot = slotId.ifBlank { "default" }
        val cleanPhone = phoneNumber.trim()
        if (cleanPhone.isEmpty()) return

        // Prevent duplicate concurrent SetAuthenticationPhoneNumber requests for the same phone number
        if (phoneBeingSentBySlot[cleanSlot] == cleanPhone) {
            return
        }
        phoneBeingSentBySlot[cleanSlot] = cleanPhone

        if (cleanSlot == _activeSlotId.value) {
            _authError.value = null
        }

        val settings = TdApi.PhoneNumberAuthenticationSettings(
            false,
            false,
            false,
            false,
            false,
            null,
            emptyArray()
        )

        telegramClient.sendToSlot(cleanSlot, TdApi.SetAuthenticationPhoneNumber(cleanPhone, settings)) { result ->
            phoneBeingSentBySlot.remove(cleanSlot)
            if (result is TdApi.Error && cleanSlot == _activeSlotId.value) {
                val msg = result.message.orEmpty()
                if (!isBenignTdlibError(msg)) {
                    _authError.value = msg
                }
            }
        }
    }

    fun setPhoneNumber(phoneNumber: String) {
        setPhoneNumberForSlot(_activeSlotId.value, phoneNumber)
    }

    fun checkAuthenticationCode(code: String) {
        val slot = _activeSlotId.value.ifBlank { "default" }
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return
        if (!codeSubmissionInFlightBySlot.add(slot)) {
            return
        }
        _authError.value = null
        telegramClient.sendToSlot(slot, TdApi.CheckAuthenticationCode(cleanCode)) { result ->
            codeSubmissionInFlightBySlot.remove(slot)
            if (result is TdApi.Error && slot == _activeSlotId.value) {
                val msg = result.message.orEmpty()
                val currentState = _authState.value
                // Do not show an error if TDLib has already advanced to Password or Ready state
                val alreadyAdvanced = currentState is TdApi.AuthorizationStateWaitPassword ||
                    currentState is TdApi.AuthorizationStateReady
                if (!isBenignTdlibError(msg) && !alreadyAdvanced) {
                    _authError.value = msg
                }
            }
        }
    }

    fun checkPassword(password: String) {
        val slot = _activeSlotId.value.ifBlank { "default" }
        if (password.isEmpty()) return
        if (!passwordSubmissionInFlightBySlot.add(slot)) {
            return
        }
        _authError.value = null
        telegramClient.sendToSlot(slot, TdApi.CheckAuthenticationPassword(password)) { result ->
            passwordSubmissionInFlightBySlot.remove(slot)
            if (result is TdApi.Error && slot == _activeSlotId.value) {
                val msg = result.message.orEmpty()
                val currentState = _authState.value
                val alreadyReady = currentState is TdApi.AuthorizationStateReady
                if (!isBenignTdlibError(msg) && !alreadyReady) {
                    _authError.value = msg
                }
            }
        }
    }

    fun setLocalError(message: String?) {
        _authError.value = if (message.isNullOrEmpty()) null else message
    }

    fun logout() {
        _authError.value = null
        removeAccount(_activeSlotId.value)
    }
}
