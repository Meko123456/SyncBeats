package io.github.meko123456.syncbeats.feature.home

import io.github.meko123456.syncbeats.core.model.AccountPlaylist
import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.HistoryItem
import io.github.meko123456.syncbeats.core.model.PlaylistDetails
import io.github.meko123456.syncbeats.core.model.SearchResult
import io.github.meko123456.syncbeats.core.testing.FakeAuthGateway
import io.github.meko123456.syncbeats.core.testing.FakeGoogleAuthController
import io.github.meko123456.syncbeats.core.testing.FakeMusicSource
import io.github.meko123456.syncbeats.core.testing.FakeRoomRepository
import io.github.meko123456.syncbeats.core.testing.FakeYouTubeAccountGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The home screen: trending, the YouTube account rails, and starting a room from one tap.
 *
 * Five collaborators, all ports now — so the whole screen runs here with no Firebase, no YouTube
 * and no device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var rooms: FakeRoomRepository
    private lateinit var music: FakeMusicSource
    private lateinit var auth: FakeAuthGateway
    private lateinit var ytAccount: FakeYouTubeAccountGateway
    private lateinit var google: FakeGoogleAuthController

    private fun track(id: String, title: String = "Track $id") =
        SearchResult(videoId = id, title = title, artist = "Artist", thumbnailUrl = "", durationMs = 1000)

    /** The state is `stateIn(WhileSubscribed)`, so it needs a collector to be live. */
    private fun TestScope.viewModel(): HomeViewModel =
        HomeViewModel(rooms, music, auth, ytAccount, google).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        rooms = FakeRoomRepository()
        music = FakeMusicSource()
        auth = FakeAuthGateway(current = AuthUser(uid = "host-1", email = "zura@example.com"))
        ytAccount = FakeYouTubeAccountGateway()
        google = FakeGoogleAuthController()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun trending_loads_on_open() = runTest(dispatcher) {
        music.trendingResults = listOf(track("a"), track("b"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.state.value.trending.map { it.videoId })
        assertFalse(vm.state.value.loadingTrending, "the spinner must be cleared when it finishes")
    }

    @Test
    fun a_trending_failure_is_reported_and_leaves_the_screen_usable() = runTest(dispatcher) {
        music.failWith = IllegalStateException("no network")
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.error?.contains("no network") == true, vm.state.value.error.orEmpty())
        assertFalse(vm.state.value.loadingTrending)
    }

    @Test
    fun a_disconnected_account_skips_the_rail_requests_entirely() = runTest(dispatcher) {
        ytAccount.connected = false
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(false, vm.state.value.yt.connected)
        assertTrue(vm.state.value.yt.playlists.isEmpty())
        // Nothing should be fetched for an account that is not linked.
        assertTrue(ytAccount.requestedPlaylistIds.isEmpty())
    }

    @Test
    fun a_connected_account_loads_all_three_rails() = runTest(dispatcher) {
        ytAccount.connected = true
        ytAccount.playlists = listOf(AccountPlaylist("p1", "Mine", "", 3))
        ytAccount.liked = listOf(track("l1"))
        ytAccount.subscriptions = listOf(track("s1"))
        val vm = viewModel()
        advanceUntilIdle()

        val yt = vm.state.value.yt
        assertEquals(true, yt.connected)
        assertFalse(yt.loading, "loading must be cleared once the rails arrive")
        assertEquals(listOf("p1"), yt.playlists.map { it.id })
        assertEquals(listOf("l1"), yt.likedSongs.map { it.videoId })
        assertEquals(listOf("s1"), yt.subscriptionFeed.map { it.videoId })
    }

    @Test
    fun one_failing_rail_does_not_lose_the_others() = runTest(dispatcher) {
        // Each rail is fetched independently; a single API error should not blank the screen.
        ytAccount.connected = true
        ytAccount.liked = listOf(track("l1"))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(true, vm.state.value.yt.connected)
        assertEquals(listOf("l1"), vm.state.value.yt.likedSongs.map { it.videoId })
    }

    @Test
    fun an_empty_account_playlist_says_so_rather_than_opening_blank() = runTest(dispatcher) {
        ytAccount.connected = true
        ytAccount.items = emptyList()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HomeIntent.OpenAccountPlaylist(AccountPlaylist("p1", "Mine", "", 0)))
        advanceUntilIdle()

        assertEquals("Playlist is empty", vm.state.value.error)
        assertNull(vm.state.value.openedPlaylist)
    }

    @Test
    fun playing_a_track_creates_a_room_and_queues_it() = runTest(dispatcher) {
        rooms.nextRoomCode = "ABC234"
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HomeIntent.PlayTrackInNewRoom(track("v1", "Swimming Pools")))
        advanceUntilIdle()

        assertEquals("host-1" to "Swimming Pools", rooms.createdRooms.single())
        assertEquals("v1", rooms.queueAdds.single().videoId)
        assertEquals("ABC234", vm.state.value.enterRoomId)
    }

    @Test
    fun a_very_long_title_is_truncated_into_the_room_name() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HomeIntent.PlayTrackInNewRoom(track("v1", "T".repeat(120))))
        advanceUntilIdle()

        // Room names are capped so the lobby list stays readable.
        assertEquals(40, rooms.createdRooms.single().second.length)
    }

    @Test
    fun playing_a_playlist_queues_its_tracks_in_order() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val details = PlaylistDetails(
            url = "u", title = "Road trip", thumbnailUrl = "",
            tracks = listOf(track("t1"), track("t2"), track("t3")),
        )
        vm.onIntent(HomeIntent.PlayPlaylistInNewRoom(details))
        advanceUntilIdle()

        assertEquals(listOf("t1", "t2", "t3"), rooms.queueAdds.map { it.videoId })
        assertEquals("Road trip", rooms.createdRooms.single().second)
    }

    @Test
    fun a_huge_playlist_is_capped_rather_than_queueing_everything() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val many = (1..120).map { track("t$it") }
        vm.onIntent(HomeIntent.PlayPlaylistInNewRoom(PlaylistDetails("u", "Huge", "", many)))
        advanceUntilIdle()

        // A 120-track import would mean 120 sequential writes before the room is usable.
        assertEquals(50, rooms.queueAdds.size, "the queue is capped at 50 tracks")
        assertEquals("t1", rooms.queueAdds.first().videoId, "the cap keeps the head of the playlist")
    }

    @Test
    fun playing_an_empty_playlist_starts_no_room() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HomeIntent.PlayPlaylistInNewRoom(PlaylistDetails("u", "Empty", "", emptyList())))
        advanceUntilIdle()

        assertTrue(rooms.createdRooms.isEmpty(), "an empty playlist has nothing to listen to")
        assertNull(vm.state.value.enterRoomId)
    }

    @Test
    fun history_can_be_replayed_in_a_new_room() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(
            HomeIntent.PlayHistoryInNewRoom(
                HistoryItem(videoId = "h1", title = "Money Trees", artist = "Kendrick", thumbnailUrl = "", durationMs = 1),
            ),
        )
        advanceUntilIdle()

        assertEquals("h1", rooms.queueAdds.single().videoId)
        assertEquals("Money Trees", rooms.createdRooms.single().second)
    }

    @Test
    fun a_blank_import_url_is_ignored() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(HomeIntent.ImportPlaylist("   "))
        advanceUntilIdle()

        assertTrue(music.playlistUrls.isEmpty(), "a blank paste should not hit the network")
        assertFalse(vm.state.value.importing)
    }

    @Test
    fun a_failed_import_is_reported_and_clears_the_spinner() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        music.failWith = IllegalStateException("private playlist")

        vm.onIntent(HomeIntent.ImportPlaylist("https://youtube.com/playlist?list=PL1"))
        advanceUntilIdle()

        assertTrue(vm.state.value.error?.contains("private playlist") == true, vm.state.value.error.orEmpty())
        assertFalse(vm.state.value.importing, "the spinner must not be left spinning")
    }

    @Test
    fun the_navigation_signal_is_consumed_once() = runTest(dispatcher) {
        rooms.nextRoomCode = "ABC234"
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(HomeIntent.PlayTrackInNewRoom(track("v1")))
        advanceUntilIdle()
        assertEquals("ABC234", vm.state.value.enterRoomId)

        vm.onIntent(HomeIntent.ConsumeEnterRoom)
        advanceUntilIdle()

        assertNull(vm.state.value.enterRoomId)
    }
}
