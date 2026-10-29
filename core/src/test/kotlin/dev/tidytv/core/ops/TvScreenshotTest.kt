package dev.tidytv.core.ops

import dev.tidytv.core.testing.FakeTv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvScreenshotTest {
    @Test fun takesAPictureAndLeavesNothingOnTheTv() = runTest {
        val tv = FakeTv.googleTv()
        val png = TvScreenshot.take(tv)
        assertNotNull(png)
        assertEquals(0x89.toByte(), png!![0])
        assertTrue("deleted from the TV", tv.pull(TvScreenshot.PATH) == null)
        assertTrue(tv.commands.contains(TvScreenshot.CLEAN))
    }

    @Test fun aTvThatCantMakeOneGivesNothing() = runTest {
        val tv = FakeTv.googleTv()
        val broken = object : TvShell by tv {
            override suspend fun run(command: String) = if (command == TvScreenshot.TAKE) "screencap: not allowed\n" else tv.run(command)
        }
        assertNull(TvScreenshot.take(broken))
    }
}
