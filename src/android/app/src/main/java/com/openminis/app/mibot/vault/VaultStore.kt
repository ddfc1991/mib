package com.openminis.app.mibot.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [fork/mibot-v2] Android Keystore AES-256-GCM. The key never leaves the
 * keystore; ciphertext is stored as base64(iv) + ":" + base64(ct).
 */
internal object VaultCrypto {
    private const val ALIAS = "mibot_vault_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    fun decrypt(enc: String): String {
        val parts = enc.split(":")
        require(parts.size == 2) { "bad ciphertext" }
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
}

/**
 * [fork/mibot-v2] The Account Vault (账号台账): accounts, encrypted secrets,
 * AI-use permission, status/check-in results, and the audit log. Stored as
 * JSON in app-private storage; observable through [entries] / [audit].
 *
 * Plaintext secrets only exist transiently inside [revealSecret], which is
 * internal to the vault package (VaultBroker / status & check-in runners).
 */
object VaultStore {
    private const val MAX_AUDIT = 500
    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    private val _audit = MutableStateFlow<List<VaultAuditEvent>>(emptyList())
    @Volatile private var loaded = false
    private val lock = Any()

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "vault").apply { mkdirs() }
    private fun entriesFile(ctx: Context) = File(dir(ctx), "vault.json")
    private fun auditFile(ctx: Context) = File(dir(ctx), "audit.json")

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            _entries.value = runCatching {
                JSONArray(entriesFile(ctx).readText()).objects().map { VaultEntry.fromJson(it) }
            }.getOrDefault(emptyList())
            _audit.value = runCatching {
                JSONArray(auditFile(ctx).readText()).objects().map { VaultAuditEvent.fromJson(it) }
            }.getOrDefault(emptyList())
            loaded = true
        }
    }

    fun entries(ctx: Context): StateFlow<List<VaultEntry>> { ensureLoaded(ctx); return _entries.asStateFlow() }
    fun audit(ctx: Context): StateFlow<List<VaultAuditEvent>> { ensureLoaded(ctx); return _audit.asStateFlow() }
    fun get(ctx: Context, id: String): VaultEntry? { ensureLoaded(ctx); return _entries.value.find { it.id == id } }

    private fun persist(ctx: Context) {
        val arr = JSONArray().apply { _entries.value.forEach { put(it.toJson()) } }
        val f = entriesFile(ctx)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    private fun persistAudit(ctx: Context) {
        val arr = JSONArray().apply { _audit.value.forEach { put(it.toJson()) } }
        auditFile(ctx).writeText(arr.toString())
    }

    /** Create or update. [newSecret] null = keep the stored secret; "" = clear it. */
    fun upsert(ctx: Context, entry: VaultEntry, newSecret: String?): VaultEntry {
        ensureLoaded(ctx)
        synchronized(lock) {
            val enc = when {
                newSecret == null -> entry.secretEnc
                newSecret.isEmpty() -> null
                else -> VaultCrypto.encrypt(newSecret)
            }
            val saved = entry.copy(secretEnc = enc, updatedAt = System.currentTimeMillis())
            val list = _entries.value.toMutableList()
            val i = list.indexOfFirst { it.id == saved.id }
            if (i >= 0) list[i] = saved else list.add(saved)
            _entries.value = list
            persist(ctx)
            return saved
        }
    }

    fun delete(ctx: Context, id: String) {
        ensureLoaded(ctx)
        synchronized(lock) {
            _entries.value = _entries.value.filterNot { it.id == id }
            persist(ctx)
        }
    }

    fun updateStatus(ctx: Context, id: String, transform: (VaultStatus) -> VaultStatus) {
        ensureLoaded(ctx)
        synchronized(lock) {
            _entries.value = _entries.value.map {
                if (it.id == id) it.copy(status = transform(it.status ?: VaultStatus())) else it
            }
            persist(ctx)
        }
    }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    internal fun revealSecret(ctx: Context, entry: VaultEntry): String? =
        entry.secretEnc?.let { runCatching { VaultCrypto.decrypt(it) }.getOrNull() }

    fun log(ctx: Context, entry: VaultEntry?, actor: String, action: String, result: String) {
        ensureLoaded(ctx)
        synchronized(lock) {
            val ev = VaultAuditEvent(
                at = System.currentTimeMillis(),
                entryId = entry?.id ?: "-",
                label = entry?.let { "${it.site} · ${it.username}" } ?: "-",
                actor = actor,
                action = action.take(160),
                result = result,
            )
            _audit.value = (listOf(ev) + _audit.value).take(MAX_AUDIT)
            persistAudit(ctx)
        }
    }

    /** Debug/CI only: a few sample accounts so the screens have content. */
    fun seedDemo(ctx: Context) {
        ensureLoaded(ctx)
        if (_entries.value.isNotEmpty()) return
        val now = System.currentTimeMillis()
        upsert(ctx, VaultEntry(newId(), "openrouter.ai", "me@example.com", "AI 服务", permission = VaultPermission.AUTO,
            manifestId = "openrouter", status = VaultStatus(credits = "$4.82", quota = "50 req/day", updatedAt = now - 3_600_000)), "sk-or-demo")
        upsert(ctx, VaultEntry(newId(), "cloud.siliconflow.cn", "13800000000", "AI 服务", permission = VaultPermission.ASK,
            manifestId = "siliconflow", status = VaultStatus(balance = "¥14.00", updatedAt = now - 7_200_000)), "sk-demo")
        upsert(ctx, VaultEntry(newId(), "mail.example.com", "work@example.com", "邮箱", permission = VaultPermission.ASK), "demo")
        upsert(ctx, VaultEntry(newId(), "vps.example.net", "root", "云服务器", permission = VaultPermission.DISABLED,
            status = VaultStatus(expiry = "2027-03-01", updatedAt = now - 86_400_000)), "demo")
        log(ctx, _entries.value.firstOrNull(), "agent", "browser_use: sign in", "allowed")
    }
}
