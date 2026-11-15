package me.xdan.aperture.platform

import org.json.JSONArray
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/** One `.properties` file per store in the desktop app's data directory. */
class PropertiesStores(private val directory: File) : KeyValueStores {
    private val stores = ConcurrentHashMap<String, PropertiesStore>()

    override fun open(name: String): KeyValueStore =
        stores.getOrPut(name) { PropertiesStore(File(directory, "$name.properties")) }
}

private class PropertiesStore(private val file: File) : KeyValueStore {
    private val values = Properties().apply {
        if (file.isFile) runCatching { file.inputStream().use(::load) }
    }

    @Synchronized private fun raw(key: String): String? = values.getProperty(key)

    override fun contains(key: String) = raw(key) != null
    override fun getBoolean(key: String, defaultValue: Boolean) = raw(key)?.toBooleanStrictOrNull() ?: defaultValue
    override fun getInt(key: String, defaultValue: Int) = raw(key)?.toIntOrNull() ?: defaultValue
    override fun getLong(key: String, defaultValue: Long) = raw(key)?.toLongOrNull() ?: defaultValue
    override fun getString(key: String, defaultValue: String?) = raw(key) ?: defaultValue
    override fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? = raw(key)?.let { json ->
        runCatching { JSONArray(json).let { array -> (0 until array.length()).map(array::getString).toSet() } }.getOrNull()
    } ?: defaultValue

    override fun edit(): KeyValueStore.Editor = Editor()

    @Synchronized private fun commit(changes: List<(Properties) -> Unit>) {
        changes.forEach { it(values) }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.outputStream().use { values.store(it, null) }
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }

    private inner class Editor : KeyValueStore.Editor {
        private val changes = mutableListOf<(Properties) -> Unit>()
        private fun put(key: String, value: String?) = apply {
            changes += { if (value == null) it.remove(key) else it.setProperty(key, value) }
        }

        override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())
        override fun putInt(key: String, value: Int) = put(key, value.toString())
        override fun putLong(key: String, value: Long) = put(key, value.toString())
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.let { JSONArray(it).toString() })
        override fun remove(key: String) = put(key, null)
        override fun clear() = apply { changes += { it.clear() } }
        override fun apply() = commit(changes.toList())
    }
}
