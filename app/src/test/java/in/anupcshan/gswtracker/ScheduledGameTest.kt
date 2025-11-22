package `in`.anupcshan.gswtracker

import `in`.anupcshan.gswtracker.data.api.NbaApiClient
import `in`.anupcshan.gswtracker.data.api.NbaApiService
import `in`.anupcshan.gswtracker.data.model.GameState
import `in`.anupcshan.gswtracker.data.repository.GameRepository
import `in`.anupcshan.gswtracker.ui.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test for scheduled game scenario (GSW vs POR on Nov 21, 2025)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduledGameTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var viewModel: GameViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        // Set main dispatcher for coroutines - use UnconfinedTestDispatcher for eager execution
        Dispatchers.setMain(testDispatcher)

        // Setup MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path?.contains("todaysScoreboard_00.json") == true -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(TestUtils.loadJsonResource("scenarios/20251121_gsw_vs_por_scheduled/scoreboard.json"))
                    }
                    request.path?.contains("scheduleLeagueV2.json") == true -> {
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(TestUtils.loadJsonResource("scenarios/20251121_gsw_vs_por_scheduled/schedule.json"))
                    }
                    request.path?.contains("boxscore") == true -> {
                        // Return empty boxscore for now (no arena data in scheduled games)
                        MockResponse()
                            .setResponseCode(404)
                            .setBody("{}")
                    }
                    else -> {
                        MockResponse().setResponseCode(404)
                    }
                }
            }
        }
        mockWebServer.start()

        // Create HTTP client with URL interceptor to redirect to MockWebServer
        val urlInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url.toString()

            // Redirect NBA API URLs to MockWebServer
            val newUrl = when {
                originalUrl.contains("cdn.nba.com") -> {
                    val path = originalUrl.substringAfter("cdn.nba.com")
                    mockWebServer.url(path).toString()
                }
                else -> originalUrl
            }

            val newRequest = originalRequest.newBuilder()
                .url(newUrl)
                .build()

            chain.proceed(newRequest)
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(urlInterceptor)
            .build()

        // Pass test dispatcher to NbaApiService so IO operations use test dispatcher
        val apiService = NbaApiService(httpClient, testDispatcher)
        val repository = GameRepository(apiService)
        viewModel = GameViewModel(repository)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun `scheduled game shows correct state for GSW vs POR`() = runTest {
        // With injected test dispatcher, all coroutines run on test scheduler
        advanceUntilIdle()

        // Verify the state is GameScheduled
        val state = viewModel.gameState.value
        assert(state is GameState.GameScheduled) {
            "Expected GameScheduled state, got: $state"
        }

        // Verify game details
        val game = (state as GameState.GameScheduled).game
        assertEquals("Expected GSW to be home team", "GSW", game.homeTeam.teamTricode)
        assertEquals("Expected POR to be away team", "POR", game.awayTeam.teamTricode)
        assertEquals("Expected game status to be 1 (scheduled)", 1, game.gameStatus)

        // Verify team names
        assertEquals("Warriors", game.homeTeam.teamName)
        assertEquals("Trail Blazers", game.awayTeam.teamName)
    }

    @Test
    fun `test can load JSON resources`() {
        // Verify we can load the test JSON files
        val scoreboard = TestUtils.loadJsonResource("scenarios/20251121_gsw_vs_por_scheduled/scoreboard.json")
        assert(scoreboard.contains("gameId"))
        assert(scoreboard.contains("scoreboard"))

        val schedule = TestUtils.loadJsonResource("scenarios/20251121_gsw_vs_por_scheduled/schedule.json")
        assert(schedule.contains("leagueSchedule"))
    }
}
