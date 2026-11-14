package me.xdan.aperture.platform

/**
 * Small named key/value files. The API mirrors SharedPreferences, which backs it on Android, so
 * existing preference files and call sites carry over unchanged.
 */
interface KeyValueStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getInt(key: String, defaultValue: Int): Int
    fun getLong(key: String, defaultValue: Long): Long
    fun getString(key: String, defaultValue: String?): String?
    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>?
    fun edit(): Editor

    interface Editor {
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
    }
}

fun interface KeyValueStores {
    fun open(name: String): KeyValueStore
}
