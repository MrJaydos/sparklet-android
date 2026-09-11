package com.sparklet.android.invite

import android.net.Uri

// Parses the refId out of an invite URL — https://sparkletapp.com/invite/<id>.
// Mirrors iOS's InviteLink.
//
// The parsing itself works on a plain path string rather than a Uri so it can
// be unit-tested on the JVM: android.net.Uri is a framework class with no
// implementation outside an instrumented environment, so a Uri-only API would
// force this logic to be tested on a device, which is exactly where it's most
// awkward to exercise edge cases.
object InviteLink {
    fun refId(from: Uri): String? = refId(path = from.path)

    fun refId(path: String?): String? {
        val parts = path.orEmpty().split('/').filter { it.isNotEmpty() }
        if (parts.size != 2 || parts[0] != "invite") return null
        return parts[1]
    }
}
