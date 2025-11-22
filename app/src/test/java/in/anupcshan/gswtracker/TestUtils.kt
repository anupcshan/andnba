package `in`.anupcshan.gswtracker

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Test utilities for loading test resources
 */
object TestUtils {

    /**
     * Load JSON file from test resources
     * @param path Path relative to test/resources directory
     * @return JSON content as string
     */
    fun loadJsonResource(path: String): String {
        val inputStream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Resource not found: $path")

        return BufferedReader(InputStreamReader(inputStream)).use { reader ->
            reader.readText()
        }
    }
}
