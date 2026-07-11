package me.xdan.aperture.util

import android.os.Environment
import java.io.File

object MediaScanner {
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")

    fun scanDirectories(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val allFiles = mutableListOf<File>()
        
        // Targeted scan of common video directories
        val targets = listOf(
            "Movies",
            "TV Shows",
            "TV",
            "Shows",
            "Download",
            "DCIM",
            "Videos"
        )
        targets.forEach { target ->
            val dir = File(root, target)
            if (dir.exists() && dir.isDirectory) {
                scanDir(dir, allFiles)
            }
        }
        
        return allFiles
    }

    private fun scanDir(dir: File, result: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                // Prevent infinite recursion and skip hidden directories
                if (!file.name.startsWith(".")) {
                    scanDir(file, result)
                }
            } else {
                if (file.extension.lowercase() in VIDEO_EXTENSIONS) {
                    result.add(file)
                }
            }
        }
    }
}
