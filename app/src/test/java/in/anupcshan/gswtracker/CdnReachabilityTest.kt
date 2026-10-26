package `in`.anupcshan.gswtracker

import androidx.test.core.app.ApplicationProvider
import `in`.anupcshan.gswtracker.data.api.NbaApiClient
import `in`.anupcshan.gswtracker.data.api.NbaApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CdnReachabilityTest {

    @Test
    fun `production client reaches and parses the live scoreboard`() {
        val client = NbaApiClient(ApplicationProvider.getApplicationContext())
        val service = NbaApiService(client.httpClient)

        val result = runBlocking { service.getScoreboard() }

        assertTrue(
            "Expected success, got ${result.exceptionOrNull()}",
            result.isSuccess
        )
    }
}
