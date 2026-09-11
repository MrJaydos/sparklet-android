package com.sparklet.android

import com.sparklet.android.invite.InviteLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    @Test
    fun `extracts refId from an invite path`() {
        assertEquals("abc123", InviteLink.refId("/invite/abc123"))
    }

    @Test
    fun `tolerates a trailing slash`() {
        assertEquals("abc123", InviteLink.refId("/invite/abc123/"))
    }

    @Test
    fun `rejects a path that is not an invite`() {
        assertNull(InviteLink.refId("/feed"))
        assertNull(InviteLink.refId("/profile/abc123"))
    }

    @Test
    fun `rejects an invite path with no id`() {
        assertNull(InviteLink.refId("/invite"))
        assertNull(InviteLink.refId("/invite/"))
    }

    // A deeper path is not an invite link: /invite/<id>/something would be a
    // different route, and silently treating it as an invite would POST an
    // accept for a refId the user never followed.
    @Test
    fun `rejects a deeper path`() {
        assertNull(InviteLink.refId("/invite/abc123/extra"))
    }

    @Test
    fun `rejects null and empty`() {
        assertNull(InviteLink.refId(null))
        assertNull(InviteLink.refId(""))
    }
}
