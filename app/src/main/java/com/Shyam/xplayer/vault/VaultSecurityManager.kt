package com.Shyam.xplayer.vault

import android.content.Context
import android.os.Environment
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject

/**
 * VaultSecurityManager: Handles AES-256-GCM file encryption, key management,
 * and PBKDF2-based authentication for the Private Vault.
 */
object VaultSecurityManager {

    private const val TAG = "VaultSecurityManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "XPlayerSecureVaultMasterKey"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val PREFS_NAME = "XPlayerVaultPrefs"

    private const val KEY_MASTER_HASH = "master_hash"
    private const val KEY_MASTER_SALT = "master_salt"
    private const val KEY_LOCK_TYPE = "lock_type"
    private const val KEY_SEC_QUESTION = "security_question"
    private const val KEY_SEC_ANSWER_HASH = "security_answer_hash"
    private const val KEY_SEC_ANSWER_SALT = "security_answer_salt"

    enum class LockType {
        PIN,
        PASSWORD,
        PATTERN
    }

    init {
        initKeyStoreKey()
    }

    private fun initKeyStoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_LENGTH)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.d(TAG, "Initialized AES-256 KeyStore key for Vault")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing KeyStore key: ${e.message}", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun isVaultSetup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_MASTER_HASH, null).isNullOrEmpty()
    }

    fun getLockType(context: Context): LockType {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_LOCK_TYPE, LockType.PIN.name) ?: LockType.PIN.name
        return try {
            LockType.valueOf(name)
        } catch (e: Exception) {
            LockType.PIN
        }
    }

    fun setLockType(context: Context, lockType: LockType) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LOCK_TYPE, lockType.name).apply()
    }

    fun setMasterPasscode(context: Context, passcode: String, lockType: LockType) {
        val salt = generateSalt()
        val hash = hashPasscode(passcode, salt)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MASTER_HASH, hash)
            .putString(KEY_MASTER_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_LOCK_TYPE, lockType.name)
            .apply()
        syncConfigToPersistentStorage(context)
    }

    fun verifyMasterPasscode(context: Context, input: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedHash = prefs.getString(KEY_MASTER_HASH, null) ?: return false
        val savedSaltStr = prefs.getString(KEY_MASTER_SALT, null) ?: return false
        val salt = Base64.decode(savedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPasscode(input, salt)
        return inputHash == savedHash
    }

    fun setSecurityQuestion(context: Context, question: String, answer: String) {
        val salt = generateSalt()
        val answerHash = hashPasscode(answer.trim().lowercase(), salt)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SEC_QUESTION, question)
            .putString(KEY_SEC_ANSWER_HASH, answerHash)
            .putString(KEY_SEC_ANSWER_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
        syncConfigToPersistentStorage(context)
    }

    fun getSecurityQuestion(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SEC_QUESTION, null)
    }

    fun verifySecurityAnswer(context: Context, answer: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedHash = prefs.getString(KEY_SEC_ANSWER_HASH, null) ?: return false
        val savedSaltStr = prefs.getString(KEY_SEC_ANSWER_SALT, null) ?: return false
        val salt = Base64.decode(savedSaltStr, Base64.NO_WRAP)
        val inputHash = hashPasscode(answer.trim().lowercase(), salt)
        return inputHash == savedHash
    }

    fun resetPasscodeWithSecurityAnswer(context: Context, answer: String, newPasscode: String, lockType: LockType): Boolean {
        if (!verifySecurityAnswer(context, answer)) return false
        setMasterPasscode(context, newPasscode, lockType)
        return true
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPasscode(passcode: String, salt: ByteArray): String {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    // Storage directories
    fun getVaultDirectory(context: Context): File {
        val dir = File(context.filesDir, ".xplayer_vault")
        if (!dir.exists()) {
            dir.mkdirs()
            File(dir, ".nomedia").createNewFile()
        }
        return dir
    }

    fun getVaultConfigFile(context: Context): File {
        return File(getVaultDirectory(context), ".vault_config.json")
    }

    private fun syncConfigToPersistentStorage(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject().apply {
                put(KEY_MASTER_HASH, prefs.getString(KEY_MASTER_HASH, ""))
                put(KEY_MASTER_SALT, prefs.getString(KEY_MASTER_SALT, ""))
                put(KEY_LOCK_TYPE, prefs.getString(KEY_LOCK_TYPE, LockType.PIN.name))
                put(KEY_SEC_QUESTION, prefs.getString(KEY_SEC_QUESTION, ""))
                put(KEY_SEC_ANSWER_HASH, prefs.getString(KEY_SEC_ANSWER_HASH, ""))
                put(KEY_SEC_ANSWER_SALT, prefs.getString(KEY_SEC_ANSWER_SALT, ""))
            }
            val configFile = getVaultConfigFile(context)
            configFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncing config to storage: ${e.message}")
        }
    }

    fun restoreConfigIfCleared(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_MASTER_HASH, null).isNullOrEmpty()) {
                val configFile = getVaultConfigFile(context)
                if (configFile.exists()) {
                    val json = JSONObject(configFile.readText())
                    prefs.edit()
                        .putString(KEY_MASTER_HASH, json.optString(KEY_MASTER_HASH, ""))
                        .putString(KEY_MASTER_SALT, json.optString(KEY_MASTER_SALT, ""))
                        .putString(KEY_LOCK_TYPE, json.optString(KEY_LOCK_TYPE, LockType.PIN.name))
                        .putString(KEY_SEC_QUESTION, json.optString(KEY_SEC_QUESTION, ""))
                        .putString(KEY_SEC_ANSWER_HASH, json.optString(KEY_SEC_ANSWER_HASH, ""))
                        .putString(KEY_SEC_ANSWER_SALT, json.optString(KEY_SEC_ANSWER_SALT, ""))
                        .apply()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed restoring vault config: ${e.message}")
        }
    }

    // Encryption & Decryption
    fun encryptFile(sourceFile: File, targetFile: File) {
        FileInputStream(sourceFile).use { fis ->
            encryptStreamToFile(fis, targetFile)
        }
    }

    fun encryptStreamToFile(inputStream: InputStream, targetFile: File) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv

        FileOutputStream(targetFile).use { fos ->
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    cos.write(buffer, 0, bytesRead)
                }
                cos.flush()
            }
        }
    }

    fun decryptFileToStream(encryptedFile: File, targetOutputStream: OutputStream) {
        FileInputStream(encryptedFile).use { fis ->
            val iv = ByteArray(GCM_IV_LENGTH)
            val readIv = fis.read(iv)
            if (readIv != GCM_IV_LENGTH) {
                throw IllegalStateException("Invalid encrypted file format: missing IV header")
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            CipherInputStream(fis, cipher).use { cis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (cis.read(buffer).also { bytesRead = it } != -1) {
                    targetOutputStream.write(buffer, 0, bytesRead)
                }
                targetOutputStream.flush()
            }
        }
    }

    fun decryptFile(encryptedFile: File, outputFile: File) {
        FileOutputStream(outputFile).use { fos ->
            decryptFileToStream(encryptedFile, fos)
        }
    }
}
