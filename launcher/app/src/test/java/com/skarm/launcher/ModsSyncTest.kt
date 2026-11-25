package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** The same cases run on iOS (Tests/SKModSyncTests.m). */
class ModsSyncTest {
    private fun decide(installed: Boolean, mods: String, game: String, sm: String?, sg: String?) =
        ModsSync.decide(installed, mods, game, sm, sg)

    @Test fun `no game installed does nothing`() = assertEquals(ModsSyncAction.NONE, decide(false, "a", "j", null, null))

    @Test fun `mods and nothing ever applied applies`() = assertEquals(ModsSyncAction.APPLY, decide(true, "a", "j", null, null))

    @Test fun `no mods and nothing ever applied does nothing`() = assertEquals(ModsSyncAction.NONE, decide(true, "", "j", null, null))

    @Test fun `nothing changed does nothing`() = assertEquals(ModsSyncAction.NONE, decide(true, "a", "j", "a", "j"))

    @Test fun `an added mod applies`() = assertEquals(ModsSyncAction.APPLY, decide(true, "a|b", "j", "a", "j"))

    @Test fun `the game putting its jars back applies again`() = assertEquals(ModsSyncAction.APPLY, decide(true, "a", "j2", "a", "j"))

    @Test fun `removing every mod restores once`() = assertEquals(ModsSyncAction.RESTORE, decide(true, "", "j", "a", "j"))

    @Test fun `already restored does nothing whatever the game does`() = assertEquals(ModsSyncAction.NONE, decide(true, "", "j2", "", "j"))
}
