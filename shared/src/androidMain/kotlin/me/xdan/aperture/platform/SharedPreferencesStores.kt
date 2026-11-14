package me.xdan.aperture.platform

import android.content.Context
import android.content.SharedPreferences

/** The same SharedPreferences files the app has always used. */
class SharedPreferencesStores(private val context: Context) : KeyValueStores {
    override fun open(name: String): KeyValueStore =
        SharedPreferencesStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
}

private class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun contains(key: String) = prefs.contains(key)
    override fun getBoolean(key: String, defaultValue: Boolean) = prefs.getBoolean(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = prefs.getInt(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = prefs.getLong(key, defaultValue)
    override fun getString(key: String, defaultValue: String?) = prefs.getString(key, defaultValue)
    override fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? =
        prefs.getStringSet(key, defaultValue)

    override fun edit(): KeyValueStore.Editor = Editor(prefs.edit())

    private class Editor(private val editor: SharedPreferences.Editor) : KeyValueStore.Editor {
        override fun putBoolean(key: String, value: Boolean) = apply { editor.putBoolean(key, value) }
        override fun putInt(key: String, value: Int) = apply { editor.putInt(key, value) }
        override fun putLong(key: String, value: Long) = apply { editor.putLong(key, value) }
        override fun putString(key: String, value: String?) = apply { editor.putString(key, value) }
        override fun putStringSet(key: String, values: Set<String>?) = apply { editor.putStringSet(key, values) }
        override fun remove(key: String) = apply { editor.remove(key) }
        override fun clear() = apply { editor.clear() }
        override fun apply() = editor.apply()
    }
}
