package io.github.meko123456.syncbeats.feature.room

import io.github.meko123456.syncbeats.core.domain.sync.SyncEngine
import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.PlaybackState
import io.github.meko123456.syncbeats.core.model.QueueItem
import io.github.meko123456.syncbeats.core.model.RoomMeta
import io.github.meko123456.syncbeats.core.model.SearchResult
import io.github.meko123456.syncbeats.core.testing.FakeAuthGateway
import io.github.meko123456.syncbeats.core.testing.FakeMusicSource
import io.github.meko123456.syncbeats.core.testing.FakePlayerController
import io.github.meko123456.syncbeats.core.testing.FakeRoomRepository
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The room screen — the app's most complex, and until this refactor the least reachable.
 *
 * RoomViewModel takes six collaborators and composes a real SyncEngine from four of them. Every
 * one is a port now, so the whole thing runs here against fakes: no Firebase project, no YouTube,
 * no audio device. Before, this code could only be exercised by opening a real room on a phone.
 *
 * Note the use of [runCurrent] rather than `advanceUntilIdle`: the room runs endless tickers — a
 * listener-status heartbeat and the sync engine's drift checks — so draining virtual time would
 * never return. runCurrent executes what is pending and stops, which is what these assertions need.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var rooms: FakeRoomRepository
    private lateinit var music: FakeMusicSource
    private lateinit var auth: FakeAuthGateway
    private lateinit var player: FakePlayerController

    private val roomId = "ABC234"
    private val me = AuthUser(uid = "me-1", email = "zura@example.com")

    private fun track(id: String, title: String = "Track $id") =
        SearchResult(videoId = id, title = title, artist = "Artist", thumbnailUrl = "", durationMs = 1000)

    private val store = ViewModelStore()

    private fun TestScope.viewModel(autoplay: Boolean = false): RoomViewModel {
        val syncEngine = SyncEngine(rooms, music, player, auth)
        val vm = RoomViewModel(roomId, autoplay, rooms, music, auth, syncEngine, player, backgroundScope)
        // Held in a store so the test can clear it, which cancels viewModelScope and runs
        // onCleared() — the same teardown a real screen gets.
        store.put("room", vm)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    /**
     * The room runs unbounded periodic work — a status heartbeat and the engine's drift loop — and
     * runTest drains the scheduler when the body finishes to detect leaks. Left running, that drain
     * never returns. Stopping the engine is what a cleared ViewModel does in production too.
     */
    private fun roomTest(body: suspend TestScope.() -> Unit): TestResult = runTest(dispatcher) {
        try {
            body()
        } finally {
            // The room runs unbounded periodic work: a status heartbeat in viewModelScope and the
            // engine's drift loop. runTest drains the scheduler after the body to detect leaks, so
            // anything still ticking makes that drain never return. Clearing the store cancels
            // viewModelScope and calls onCleared(), which stops the engine — exactly what happens
            // when the screen goes away.
            store.clear()
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        rooms = FakeRoomRepository()
        music = FakeMusicSource()
        auth = FakeAuthGateway(current = me, storedUsername = "Zura")
        player = FakePlayerController()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun opening_a_room_joins_it() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "someone-else"))
        viewModel()
        runCurrent()

        assertEquals(roomId to "me-1", rooms.joined.single())
    }

    @Test
    fun the_room_state_reflects_what_the_repository_publishes() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "me-1"))
        rooms.emitQueue(listOf(QueueItem(key = "k1", videoId = "v1", title = "Money Trees")))
        val vm = viewModel()
        runCurrent()

        assertEquals("Friday night", vm.state.value.meta?.name)
        assertTrue(vm.state.value.isHost, "the host id matches the signed-in user")
        assertEquals(listOf("v1"), vm.state.value.queue.map { it.videoId })
    }

    @Test
    fun a_listener_is_not_the_host() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "someone-else"))
        val vm = viewModel()
        runCurrent()

        assertTrue(!vm.state.value.isHost)
    }

    @Test
    fun searching_populates_results_and_clears_the_spinner() = roomTest {
        music.searchResults = listOf(track("s1"), track("s2"))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.Search("kendrick"))
        runCurrent()

        assertEquals(listOf("kendrick"), music.searches)
        assertEquals(listOf("s1", "s2"), vm.state.value.searchResults.map { it.videoId })
        assertTrue(!vm.state.value.searching)
    }

    @Test
    fun a_failed_search_is_reported_as_a_sentence() = roomTest {
        music.failWith = IllegalStateException("no network")
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.Search("kendrick"))
        runCurrent()

        // ErrorCopy turns the throwable into something a listener can act on.
        assertTrue(vm.state.value.error != null)
        assertTrue(!vm.state.value.searching, "the spinner must not be left on")
    }

    @Test
    fun clearing_the_search_empties_the_results() = roomTest {
        music.searchResults = listOf(track("s1"))
        val vm = viewModel()
        runCurrent()
        vm.onIntent(RoomIntent.Search("x"))
        runCurrent()

        vm.onIntent(RoomIntent.ClearSearch)
        runCurrent()

        assertTrue(vm.state.value.searchResults.isEmpty())
    }

    @Test
    fun queueing_into_a_room_that_is_already_playing_appends_rather_than_hijacking() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "me-1"))
        rooms.emitPlayback(PlaybackState(videoId = "playing-now", title = "Money Trees", isPlaying = true))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.AddToQueueAndMaybePlay(track("new1")))
        runCurrent()

        // Something is already playing, so the track goes to the back of the queue.
        assertEquals("new1", rooms.queueAdds.single().videoId)
    }

    @Test
    fun queueing_into_a_hostless_room_takes_control_first() = roomTest {
        // A room whose host left: whoever queues next should become the host.
        rooms.emitMeta(RoomMeta(name = "Orphaned", hostId = ""))
        rooms.emitPlayback(null)
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.AddToQueueAndMaybePlay(track("first")))
        runCurrent()

        assertEquals("me-1", rooms.observeMeta(roomId).let { vm.state.value.meta?.hostId })
    }

    @Test
    fun removing_a_queued_track_drops_it() = roomTest {
        val item = QueueItem(key = "k1", videoId = "v1", title = "Money Trees")
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "me-1"))
        rooms.emitQueue(listOf(item))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.RemoveFromQueue(item))
        runCurrent()

        assertTrue(vm.state.value.queue.isEmpty())
    }

    @Test
    fun chat_is_sent_with_the_typed_text() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "me-1"))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.SendChat("  hello everyone  "))
        runCurrent()

        assertEquals(1, rooms.chatSent.size)
        assertTrue(rooms.chatSent.single().contains("hello everyone"))
    }

    @Test
    fun an_empty_chat_message_is_not_sent() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "me-1"))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.SendChat("   "))
        runCurrent()

        assertTrue(rooms.chatSent.isEmpty(), "blank messages should not reach the room")
    }

    @Test
    fun saving_an_unnamed_room_falls_back_to_its_code() = roomTest {
        rooms.emitMeta(RoomMeta(name = "", hostId = "me-1"))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.ToggleSaveRoom)
        runCurrent()

        assertEquals("Room $roomId", rooms.savedRoomCalls.single().third)
    }

    @Test
    fun leaving_the_room_drops_presence_and_stops_this_device() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "someone-else"))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.LeaveRoom)
        runCurrent()

        assertEquals(roomId to "me-1", rooms.left.single())
        assertTrue(player.stopped, "playback must stop on this device when you leave")
    }

    @Test
    fun taking_control_makes_you_the_host() = roomTest {
        rooms.emitMeta(RoomMeta(name = "Friday night", hostId = "someone-else"))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(RoomIntent.TakeControl)
        runCurrent()

        assertTrue(vm.state.value.isHost)
    }
}
