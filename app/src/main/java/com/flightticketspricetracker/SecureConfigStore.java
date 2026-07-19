package com.flightticketspricetracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureConfigStore {
    private static final String PREFS = "provider_config";
    private static final String KEY_BLOB = "encrypted_provider_config_v1";
    private static final String KEY_ALIAS = "flight_tracker_provider_config";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences preferences;

    public SecureConfigStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(ProviderConfig config) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(config.encode().getBytes(StandardCharsets.UTF_8));
            String blob = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            preferences.edit().putString(KEY_BLOB, blob).apply();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect provider configuration.", exception);
        }
    }

    public ProviderConfig load() {
        String defaultBackendUrl = BuildConfig.DEFAULT_BACKEND_URL;
        String blob = preferences.getString(KEY_BLOB, "");
        if (blob == null || blob.isEmpty()) return ProviderConfig.empty(defaultBackendUrl);
        try {
            String[] parts = blob.split(":", 2);
            if (parts.length != 2) return ProviderConfig.empty(defaultBackendUrl);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            );
            String decoded = new String(
                    cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                    StandardCharsets.UTF_8
            );
            return ProviderConfig.decode(decoded, defaultBackendUrl);
        } catch (Exception exception) {
            preferences.edit().remove(KEY_BLOB).apply();
            return ProviderConfig.empty(defaultBackendUrl);
        }
    }

    public void clear() {
        preferences.edit().remove(KEY_BLOB).apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
