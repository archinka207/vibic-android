package com.example.vibic.channel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeChannelAdminRepositoryTest {

    private val ch = "ch1"
    private var me = "owner"
    private lateinit var repo: FakeChannelAdminRepository

    @Before
    fun setUp() = runBlocking {
        me = "owner"
        repo = FakeChannelAdminRepository(currentUserId = { me }, ownerPassword = "secret", clock = { 42L })
        repo.seedChannel(ch, ownerId = "owner")
        repo.seedUser("alice", "Alice")
    }

    private fun <T> Result<T>.error() = exceptionOrNull()

    @Test
    fun roles() = runBlocking {
        repo.promoteToAdmin(ch, "alice").getOrThrow()
        val staff = repo.getAdmins(ch).getOrThrow()
        assertEquals(ChannelRole.OWNER, staff.myRole("owner"))
        assertEquals(ChannelRole.ADMIN, staff.myRole("alice"))
        assertEquals(ChannelRole.SUBSCRIBER, staff.myRole("bob"))
        assertEquals(AdminRights.ALL, staff.rightsOf("owner"))
    }

    @Test
    fun newAdminGetsDefaultRights() = runBlocking {
        val a = repo.promoteToAdmin(ch, "alice").getOrThrow()
        assertEquals(AdminRights(postMessages = true, inviteUsers = true, viewStats = true), a.rights)
        assertEquals("Alice", a.name)
        assertEquals("owner", a.promotedBy)
        assertEquals(42L, a.promotedAt)
    }

    @Test
    fun ownerRightsCannotBeChanged() = runBlocking {
        assertEquals(ChannelAdminError.OwnerRightsLocked, repo.updateRights(ch, "owner", AdminRights.NONE).error())
        assertEquals(ChannelAdminError.OwnerRightsLocked, repo.demote(ch, "owner").error())
        assertEquals(ChannelAdminError.OwnerRightsLocked, repo.promoteToAdmin(ch, "owner").error())
    }

    @Test
    fun adminWithoutAddAdminsCannotPromote() = runBlocking {
        repo.promoteToAdmin(ch, "alice").getOrThrow()
        me = "alice"
        assertEquals(ChannelAdminError.NotAllowed, repo.promoteToAdmin(ch, "bob").error())
    }

    @Test
    fun adminGrantsOnlyOwnRights() = runBlocking {
        val aliceRights = AdminRights(postMessages = true, viewStats = true, addAdmins = true)
        repo.promoteToAdmin(ch, "alice", aliceRights).getOrThrow()
        me = "alice"
        assertEquals(
            ChannelAdminError.RightsExceedOwn,
            repo.promoteToAdmin(ch, "bob", AdminRights(banUsers = true)).error(),
        )
        assertTrue(repo.promoteToAdmin(ch, "bob", AdminRights(postMessages = true)).isSuccess)
    }

    @Test
    fun demoteOnlyByOwnerOrPromoter() = runBlocking {
        repo.promoteToAdmin(ch, "alice", AdminRights(postMessages = true, addAdmins = true)).getOrThrow()
        repo.promoteToAdmin(ch, "carol", AdminRights(postMessages = true, addAdmins = true)).getOrThrow()
        me = "alice"
        repo.promoteToAdmin(ch, "bob", AdminRights(postMessages = true)).getOrThrow()

        me = "carol"
        assertEquals(ChannelAdminError.NotAllowed, repo.demote(ch, "bob").error())
        assertEquals(ChannelAdminError.NotAllowed, repo.demote(ch, "alice").error())

        me = "alice"
        assertTrue(repo.demote(ch, "bob").isSuccess)

        me = "owner"
        assertTrue(repo.demote(ch, "carol").isSuccess)
    }

    @Test
    fun transferOwnership() = runBlocking {
        repo.promoteToAdmin(ch, "alice").getOrThrow()

        me = "alice"
        assertEquals(ChannelAdminError.NotAllowed, repo.transferOwnership(ch, "alice", "secret").error())

        me = "owner"
        assertEquals(ChannelAdminError.NotAdmin, repo.transferOwnership(ch, "bob", "secret").error())
        assertEquals(ChannelAdminError.WrongPassword, repo.transferOwnership(ch, "alice", "nope").error())

        val staff = repo.transferOwnership(ch, "alice", "secret").getOrThrow()
        assertEquals("alice", staff.ownerId)
        assertEquals(listOf("owner"), staff.admins.map { it.userId })
        assertEquals(AdminRights.ALL, staff.rightsOf("owner"))
        assertEquals(ChannelRole.ADMIN, staff.myRole("owner"))
    }

    @Test
    fun failedActionLeavesStateUntouched() = runBlocking {
        repo.promoteToAdmin(ch, "alice").getOrThrow()
        val before = repo.getAdmins(ch).getOrThrow()
        repo.transferOwnership(ch, "alice", "wrong")
        assertEquals(before, repo.getAdmins(ch).getOrThrow())
    }

    @Test
    fun editorKeepsRightsHeCannotGrant() = runBlocking {
        repo.promoteToAdmin(ch, "alice", AdminRights(postMessages = true, banUsers = true, addAdmins = true)).getOrThrow()
        me = "alice"
        repo.promoteToAdmin(ch, "bob", AdminRights(postMessages = true, banUsers = true)).getOrThrow()
        // Алиса теряет право блокировать, но у Боба оно остаётся
        me = "owner"
        repo.updateRights(ch, "alice", AdminRights(postMessages = true, viewStats = true, addAdmins = true)).getOrThrow()

        me = "alice"
        // Оставить banUsers можно, выдать новое право, которого у Алисы нет, — нельзя
        assertTrue(repo.updateRights(ch, "bob", AdminRights(postMessages = true, banUsers = true, viewStats = true)).isSuccess)
        assertEquals(
            ChannelAdminError.RightsExceedOwn,
            repo.updateRights(ch, "bob", AdminRights(banUsers = true, changeProfile = true)).error(),
        )
    }
}
