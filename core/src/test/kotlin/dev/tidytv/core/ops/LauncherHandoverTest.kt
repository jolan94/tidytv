package dev.tidytv.core.ops

import dev.tidytv.core.lists.HomeApp
import dev.tidytv.core.lists.Launchers
import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherHandoverTest {
    private val apk = ByteArray(37_287)
    private val launcherx = Launchers.LAUNCHERX + "/.MainActivity"

    private fun handover(tv: FakeTv, log: ActionLog = MemoryActionLog()) = LauncherHandover(tv, log, clock = { 5L }, sleep = {})

    @Test fun googleTvHandsHomeOverAndDisablesLauncherx() = runTest {
        val tv = FakeTv.googleTv(sdk = 34)
        val log = MemoryActionLog()
        val out = handover(tv, log).run(apk, listOf("com.netflix.ninja", "com.google.android.youtube.tv"), sdk = 34)
        assertEquals(HandoverOutcome.Done(Launchers.LAUNCHERX), out)
        assertEquals(Launchers.TIDY_HOME_COMPONENT, tv.home())
        assertTrue(Launchers.LAUNCHERX in tv.disabled)
        assertTrue(Launchers.LAUNCHERX in log.records().disabledByUs())
        assertEquals(launcherx, log.records().originalHome())
        assertTrue(tv.commands.any { it.contains("--es apps com.netflix.ninja,com.google.android.youtube.tv") })
        assertTrue("Android 14 never gets the role command: it hangs once the setup app is off", tv.commands.none { it.startsWith("cmd role add-role-holder") })
        assertTrue(tv.commands.any { it.startsWith("cmd package set-home-activity") })
    }

    @Test fun androidTenToThirteenStillSetTheHomeRole() = runTest {
        val tv = FakeTv.googleTv(sdk = 33)
        handover(tv).run(apk, emptyList(), sdk = 33)
        assertTrue(tv.commands.any { it.startsWith("cmd role add-role-holder") })
        val old = FakeTv.googleTv(sdk = 28)
        handover(old).run(apk, emptyList(), sdk = 28)
        assertTrue("Android 9 has no roles", old.commands.none { it.startsWith("cmd role add-role-holder") })
    }

    @Test fun startIsConfirmedBeforeStockLauncherIsTouched() = runTest {
        val tv = FakeTv.googleTv()
        handover(tv).run(apk, emptyList(), sdk = 30)
        val start = tv.commands.indexOfFirst { it.startsWith("am start -W") }
        val disable = tv.commands.indexOfFirst { it.startsWith("pm disable-user") }
        val install = tv.commands.indexOfFirst { it == Commands.INSTALL_TMP }
        assertTrue(install in 0 until start)
        assertTrue(start < disable)
    }

    @Test fun usesPushThenPmInstallNeverAdbInstall() = runTest {
        val tv = FakeTv.googleTv()
        handover(tv).run(apk, emptyList(), sdk = 30)
        assertEquals(apk.size, tv.pushed[Commands.APK_TMP]!!.size)
        assertTrue(Commands.APK_TMP !in tv.files)
    }

    @Test fun androidTv9ClassicLauncher() = runTest {
        val tv = FakeTv.androidTv9()
        val out = handover(tv).run(apk, emptyList(), sdk = 28)
        assertEquals(HandoverOutcome.Done("com.google.android.tvlauncher"), out)
        assertTrue(tv.commands.none { it.startsWith("cmd role") })
    }

    @Test fun homeAlreadyOursDisablesNothing() = runTest {
        val tv = FakeTv.googleTv()
        handover(tv).run(apk, emptyList(), sdk = 30)
        val tv2 = tv.apply { commands.clear() }
        val out = handover(tv2).run(apk, emptyList(), sdk = 30)
        assertEquals(HandoverOutcome.Done(null), out)
        assertTrue(tv2.commands.none { it.startsWith("pm disable") })
    }

    @Test fun chooserWithOneKnownStockLauncherPicksIt() = runTest {
        val tv = FakeTv.androidTv9().apply {
            installed += "com.other.home"
            homeClaimants["com.other.home/.Main"] = 2
        }
        assertTrue(tv.home()!!.startsWith("android/"))
        val out = handover(tv).run(apk, emptyList(), sdk = 28)
        assertEquals(HandoverOutcome.Done("com.google.android.tvlauncher"), out)
    }

    @Test fun chooserWithTwoKnownStockLaunchersAsks() = runTest {
        val tv = FakeTv.androidTv9().apply {
            installed += "com.google.android.leanbacklauncher"
            homeClaimants["com.google.android.leanbacklauncher/.MainActivity"] = 2
        }
        val out = handover(tv).run(apk, emptyList(), sdk = 28)
        assertTrue(out is HandoverOutcome.NeedsChoice)
        assertTrue("nothing installed", tv.pushed.isEmpty())
    }

    @Test fun unknownHomeOwnerChangesNothing() = runTest {
        val tv = FakeTv.googleTv().apply {
            installed += "com.wolf.firelauncher"
            homeClaimants["com.wolf.firelauncher/.Home"] = 5
            preferredHome = "com.wolf.firelauncher/.Home"
        }
        val out = handover(tv).run(apk, emptyList(), sdk = 30)
        assertEquals(HandoverOutcome.UnknownHome("com.wolf.firelauncher"), out)
        assertTrue(tv.pushed.isEmpty())
    }

    @Test fun signatureConflictIsReportedThenRemovedOnRequest() = runTest {
        val tv = FakeTv.googleTv().apply {
            installed += Launchers.TIDY_HOME_PACKAGE
            installAnswer = "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.ansub.tvhome signatures do not match]"
        }
        val h = handover(tv)
        assertEquals(HandoverOutcome.SignatureConflict, h.run(apk, emptyList(), sdk = 30))
        assertEquals(launcherx, tv.home())
        assertTrue(h.removeConflictingCopy())
        tv.installAnswer = "Success"
        assertTrue(h.run(apk, emptyList(), sdk = 30) is HandoverOutcome.Done)
    }

    @Test fun verificationFailureMeansLauncherIsImpossibleHere() = runTest {
        val tv = FakeTv.googleTv().apply { installAnswer = "Failure [INSTALL_FAILED_VERIFICATION_FAILURE]" }
        val out = handover(tv).run(apk, emptyList(), sdk = 30)
        assertTrue(out is HandoverOutcome.InstallBlocked)
        assertTrue(tv.disabled.isEmpty())
        assertEquals(launcherx, tv.home())
    }

    @Test fun otherInstallFailureLeavesTvUntouched() = runTest {
        val tv = FakeTv.googleTv().apply { installAnswer = "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]" }
        val out = handover(tv).run(apk, emptyList(), sdk = 30)
        assertTrue(out is HandoverOutcome.InstallFailed)
        assertTrue(tv.disabled.isEmpty())
    }

    @Test fun launcherThatDoesNotStartLeavesStockAlone() = runTest {
        val tv = FakeTv.googleTv().apply { startAnswer = "Starting: Intent { }\nStatus: ok\nError: Activity not started, unable to resolve Intent\n" }
        val out = handover(tv).run(apk, emptyList(), sdk = 30)
        assertTrue(out is HandoverOutcome.StartFailed)
        assertTrue(Launchers.LAUNCHERX !in tv.disabled)
        assertEquals(launcherx, tv.home())
    }

    /** The fallback: the setup app only claims Home after the stock launcher is off, so it can't be handled up front. */
    @Test fun setupWraithTakingHomeLateRollsBack() = runTest {
        val tv = FakeTv.googleTv(sdk = 34).apply { wraithBeatsPreference = false }
        // Model the Google TV 14 quirk: once launcherx is off, the setup app grabs Home.
        val h = handover(tv)
        tv.dropOnPrefix = null
        val shell = object : TvShell by tv {
            override suspend fun run(command: String): String {
                if (command.startsWith("pm disable-user --user 0 ${Launchers.LAUNCHERX}")) tv.wraithBeatsPreference = true
                if (command.startsWith("pm enable ${Launchers.LAUNCHERX}")) tv.wraithBeatsPreference = false
                val out = tv.run(command)
                return if (command == Commands.QUERY_HOME) out.lines().filterNot { it.contains(Launchers.SETUPWRAITH) }.joinToString("\n") else out
            }
        }
        val out = LauncherHandover(shell, MemoryActionLog(), sleep = {}).run(apk, emptyList(), sdk = 34)
        assertEquals(HandoverOutcome.SetupWraithTookHome(rolledBack = true), out)
        assertEquals(launcherx, tv.home())
        assertFalse(Launchers.LAUNCHERX in tv.disabled)
        h.hashCode()
    }

    @Test fun setupWraithCanBeTurnedOffWhenSafeThenHandoverSucceeds() = runTest {
        val tv = FakeTv.googleTv(sdk = 34)
        val log = MemoryActionLog()
        val h = handover(tv, log)
        assertEquals(WraithCheck.Ok, h.disableSetupWraith(onEthernet = false))
        assertTrue(Launchers.SETUPWRAITH in tv.disabled)
        assertTrue(Launchers.SETUPWRAITH in log.records().disabledByUs())
        assertTrue(h.run(apk, emptyList(), sdk = 34) is HandoverOutcome.Done)
    }

    @Test fun setupWraithOwningWifiIsNeverTurnedOff() = runTest {
        val tv = FakeTv.googleTv().apply { wifiOwner = "com.google.android.tungsten.setupwraith" }
        assertEquals(WraithCheck.OwnsWifi, handover(tv).disableSetupWraith(onEthernet = false))
        assertFalse(Launchers.SETUPWRAITH in tv.disabled)
    }

    @Test fun setupWraithOwningWifiIsAllowedOnEthernet() = runTest {
        val tv = FakeTv.googleTv().apply { wifiOwner = "com.google.android.tungsten.setupwraith" }
        assertEquals(WraithCheck.Ok, handover(tv).disableSetupWraith(onEthernet = true))
    }

    @Test fun setupWraithBlockedUntilFirstSetupIsDone() = runTest {
        val tv = FakeTv.googleTv().apply { settings["secure/user_setup_complete"] = "0" }
        assertEquals(WraithCheck.SetupIncomplete, handover(tv).disableSetupWraith(onEthernet = false))
    }

    @Test fun setupWraithPersistentIsLeftOn() = runTest {
        val tv = FakeTv.googleTv().apply { persistent += Launchers.SETUPWRAITH }
        assertEquals(WraithCheck.Persistent, handover(tv).disableSetupWraith(onEthernet = false))
    }

    @Test fun persistentStockLauncherIsNotDisabled() = runTest {
        val tv = FakeTv.googleTv().apply { persistent += Launchers.LAUNCHERX }
        val out = handover(tv).run(apk, emptyList(), sdk = 30)
        // The preference alone is enough on this fake; real TVs that ignore it get rolled back.
        assertTrue(out is HandoverOutcome.Done)
        assertFalse(Launchers.LAUNCHERX in tv.disabled)
    }

    @Test fun lateRollbackThatFailsGivesRescueCommands() = runTest {
        val tv = FakeTv.googleTv()
        val shell = object : TvShell by tv {
            override suspend fun run(command: String): String {
                if (command.startsWith("pm disable-user --user 0 ${Launchers.LAUNCHERX}")) tv.wraithBeatsPreference = true
                val out = tv.run(command)
                return if (command == Commands.QUERY_HOME) out.lines().filterNot { it.contains(Launchers.SETUPWRAITH) }.joinToString("\n") else out
            }
        }
        tv.enableDoesNotStick += Launchers.LAUNCHERX
        val out = LauncherHandover(shell, MemoryActionLog(), sleep = {}).run(apk, emptyList(), sdk = 30)
        assertEquals(HandoverOutcome.SetupWraithTookHome(rolledBack = false), out)
    }

    @Test fun droppedConnectionIsReported() = runTest {
        val tv = FakeTv.googleTv().apply { dropOnPrefix = "am start" }
        assertEquals(HandoverOutcome.Disconnected, handover(tv).run(apk, emptyList(), sdk = 30))
        assertEquals(launcherx, tv.home())
    }

    // ---------- The second home screen (cleanyourtv) ----------

    private fun styled(tv: FakeTv, log: ActionLog = MemoryActionLog()) =
        LauncherHandover(tv, log, clock = { 5L }, sleep = {}, app = HomeApp.STYLED).also { tv.installAs = HomeApp.STYLED }

    @Test fun styledHomeGetsDockFromConfigNotExtras() = runTest {
        val tv = FakeTv.googleTv(sdk = 34)
        val out = styled(tv).run(apk, listOf("com.netflix.ninja", "com.google.android.youtube.tv"), sdk = 34)
        assertEquals(HandoverOutcome.Done(Launchers.LAUNCHERX), out)
        assertEquals(HomeApp.STYLED.component, tv.home())
        val config = tv.pushed["/sdcard/Android/data/com.gathin.tvhome/files/config.json"]!!.decodeToString()
        assertEquals("{\"dock_apps\": [\"com.netflix.ninja\", \"com.google.android.youtube.tv\"], \"tile_menu\": true, \"app_library\": true, \"launcher_settings_button\": true}\n", config)
        assertTrue(tv.files.keys.any { it.endsWith("com.gathin.tvhome/files/theme_revision") })
        assertTrue(tv.commands.any { it.startsWith("am start -W -n com.gathin.tvhome/.MainActivity") && !it.contains("--es") })
        assertTrue(tv.commands.any { it.startsWith("pm grant com.gathin.tvhome") })
    }

    @Test fun styledConfigIsWrittenAfterTheFirstStartAndThenTheLauncherRestartsToReadIt() = runTest {
        val tv = FakeTv.googleTv()
        styled(tv).run(apk, listOf("com.netflix.ninja"), sdk = 30)
        // A folder the shell makes before the app has ever run is wiped at its first start (TCL, Android 11), so the dock
        // goes in after that start, through the launcher's provider so it owns the file, and the launcher is restarted.
        val start = tv.commands.indexOfFirst { it.startsWith("am start -W") }
        val push = tv.commands.indexOfFirst { it.contains("content write --uri content://com.gathin.tvhome.theme/config.json") }
        val restart = tv.commands.indexOfFirst { it.contains("am force-stop com.gathin.tvhome") && it.endsWith("echo restarted") }
        val setHome = tv.commands.indexOfFirst { it.startsWith("cmd package set-home-activity") }
        assertTrue("start $start, write $push, restart $restart", start in 0 until push && push < restart && restart < setHome)
    }

    @Test fun styledWithNoDockSkipsConfig() = runTest {
        val tv = FakeTv.googleTv()
        styled(tv).run(apk, emptyList(), sdk = 30)
        assertTrue(tv.pushed.keys.none { it.endsWith("config.json") })
    }

    @Test fun switchingBetweenTidyHomeScreensLeavesStockAlone() = runTest {
        val tv = FakeTv.googleTv()
        assertTrue(handover(tv).run(apk, emptyList(), sdk = 30) is HandoverOutcome.Done)
        tv.commands.clear()
        val out = styled(tv).run(apk, emptyList(), sdk = 30)
        assertEquals(HandoverOutcome.Done(null), out)
        assertEquals(HomeApp.STYLED.component, tv.home())
        assertTrue(tv.commands.none { it.startsWith("pm disable") })
    }

    @Test fun restoreRemovesWhicheverHomeScreenWasInstalled() = runTest {
        val tv = FakeTv.googleTv(sdk = 34)
        val log = MemoryActionLog()
        styled(tv, log).run(apk, listOf("com.netflix.ninja"), sdk = 34)
        assertTrue(Restorer(tv, log).run() is RestoreOutcome.Done)
        assertFalse(HomeApp.STYLED.pkg in tv.installed)
        assertEquals(launcherx, tv.home())
    }

    @Test fun ourHomeScreensAreNeverCleanUpCandidates() {
        for (app in HomeApp.entries) {
            assertTrue(Launchers.isHomeApp(app.pkg))
            assertTrue(dev.tidytv.core.lists.Protected.isProtected(app.pkg))
        }
    }
}
