package io.github.meko123456.syncbeats.feature.lobby

import io.github.meko123456.syncbeats.core.model.AuthUser
import io.github.meko123456.syncbeats.core.model.RoomMeta
import io.github.meko123456.syncbeats.core.model.SavedRoom
import io.github.meko123456.syncbeats.core.testing.FakeAuthGateway
import io.github.meko123456.syncbeats.core.testing.FakeRoomRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lobby: creating a room, and judging what somebody typed.
 *
 * The interesting behaviour here is the refusal to query for an impossible code. "OO0011" is six
 * characters long, so a length-only check would send it to Firebase and come back with
 * "No room found" — which blames the room for a typo. These tests pin the alternative.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LobbyViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var rooms: FakeRoomRepository
    private lateinit var auth: FakeAuthGateway

    /**
     * The state is exposed with `stateIn(WhileSubscribed)`, so it is cold until something
     * collects it — exactly as it behaves with a screen attached. Without a collector every
     * assertion would read the initial value and pass or fail for the wrong reason, so each
     * ViewModel here is created with one already running.
     */
    private fun TestScope.viewModel(): LobbyViewModel =
        LobbyViewModel(rooms, auth).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        rooms = FakeRoomRepository()
        auth = FakeAuthGateway(current = AuthUser(uid = "host-1", email = "zura@example.com"))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun creating_a_room_also_bookmarks_it() = runTest(dispatcher) {
        rooms.nextRoomCode = "QRS789"
        val vm = viewModel()

        vm.onIntent(LobbyIntent.CreateRoom("Friday night"))
        advanceUntilIdle()

        assertEquals("host-1" to "Friday night", rooms.createdRooms.single())
        // A room created deliberately (and named) is worth keeping in the library.
        assertEquals(Triple("host-1", "QRS789", "Friday night"), rooms.savedRoomCalls.single())
        assertEquals("QRS789", vm.state.value.enterRoomId)
    }

    @Test
    fun an_unnamed_room_gets_a_default_name() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(LobbyIntent.CreateRoom("   "))
        advanceUntilIdle()

        assertEquals("Listening room", rooms.createdRooms.single().second)
    }

    @Test
    fun a_lookalike_character_is_named_instead_of_queried() = runTest(dispatcher) {
        val vm = viewModel()

        // Six characters, so a length check would let this through to the network.
        vm.onIntent(LobbyIntent.JoinRoom("OO0011"))
        advanceUntilIdle()

        val error = vm.state.value.error
        // Assert the *reason*, not just that an "O" appears: without the guard this code reaches
        // the network and comes back "No room found for code OO0011", which also contains an O and
        // would let a broken guard pass. Mutation testing caught exactly that.
        assertTrue(
            error?.contains("too easy to mix up") == true,
            "the lookalike rule should be explained, got: $error",
        )
        assertTrue(
            rooms.findRoomCalls.isEmpty(),
            "an impossible code must never be queried; it was looked up as ${rooms.findRoomCalls}",
        )
        assertNull(vm.state.value.enterRoomId, "an invalid code must not navigate anywhere")
    }

    @Test
    fun a_code_of_the_wrong_length_says_so() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(LobbyIntent.JoinRoom("ABC"))
        advanceUntilIdle()

        assertTrue(vm.state.value.error?.contains("3") == true, vm.state.value.error.orEmpty())
    }

    @Test
    fun a_pasted_code_with_separators_is_accepted() = runTest(dispatcher) {
        rooms.existingRoom = RoomMeta(name = "Friday night", hostId = "host-1")
        val vm = viewModel()

        // People read codes aloud and paste them with spaces or dashes.
        vm.onIntent(LobbyIntent.JoinRoom("abc-234"))
        advanceUntilIdle()

        assertEquals("ABC234", vm.state.value.enterRoomId)
        assertNull(vm.state.value.error)
    }

    @Test
    fun a_valid_code_with_no_room_behind_it_reports_the_code() = runTest(dispatcher) {
        rooms.existingRoom = null
        val vm = viewModel()

        vm.onIntent(LobbyIntent.JoinRoom("ABC234"))
        advanceUntilIdle()

        assertEquals("No room found for code ABC234", vm.state.value.error)
        assertNull(vm.state.value.enterRoomId)
    }

    @Test
    fun saved_rooms_are_listed_newest_first() = runTest(dispatcher) {
        rooms.emitSavedRooms(
            listOf(
                SavedRoom(key = "OLD123", name = "Older", savedAt = 100L),
                SavedRoom(key = "NEW456", name = "Newer", savedAt = 900L),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("NEW456", "OLD123"), vm.state.value.savedRooms.map { it.key })
    }

    @Test
    fun removing_a_saved_room_drops_it_from_the_list() = runTest(dispatcher) {
        val doomed = SavedRoom(key = "ABC234", name = "Gone", savedAt = 1L)
        rooms.emitSavedRooms(listOf(doomed))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(LobbyIntent.RemoveSavedRoom(doomed))
        advanceUntilIdle()

        assertTrue(vm.state.value.savedRooms.isEmpty())
    }

    @Test
    fun a_failure_while_creating_is_reported_and_clears_loading() = runTest(dispatcher) {
        rooms.failWith = IllegalStateException("network down")
        val vm = viewModel()

        vm.onIntent(LobbyIntent.CreateRoom("Friday night"))
        advanceUntilIdle()

        assertTrue(vm.state.value.error != null)
        assertTrue(!vm.state.value.loading, "loading must not be left on after a failure")
    }

    @Test
    fun the_navigation_signal_is_consumed_once() = runTest(dispatcher) {
        rooms.nextRoomCode = "ABC234"
        val vm = viewModel()
        vm.onIntent(LobbyIntent.CreateRoom("Friday night"))
        advanceUntilIdle()
        assertEquals("ABC234", vm.state.value.enterRoomId)

        vm.onIntent(LobbyIntent.ConsumeEnterRoom)
        advanceUntilIdle()

        assertNull(vm.state.value.enterRoomId, "otherwise the app navigates again on recomposition")
    }
}
