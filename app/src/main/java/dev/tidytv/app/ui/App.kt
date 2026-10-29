package dev.tidytv.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.tidytv.app.UiState
import dev.tidytv.app.WizardViewModel
import dev.tidytv.app.ui.screens.ConnectScreen
import dev.tidytv.app.ui.screens.DoneScreen
import dev.tidytv.app.ui.screens.AllAppsScreen
import dev.tidytv.app.ui.screens.AdBlockScreen
import dev.tidytv.app.ui.screens.AppsScreen
import dev.tidytv.app.ui.screens.SpaceScreen
import dev.tidytv.app.ui.screens.HubScreen
import dev.tidytv.app.ui.screens.CheckScreen
import dev.tidytv.app.ui.screens.RescueScreen
import dev.tidytv.app.ui.screens.RemoteScreen
import dev.tidytv.app.ui.screens.SendFilesScreen
import dev.tidytv.core.ops.isOff
import dev.tidytv.core.ops.offByUs
import dev.tidytv.app.ui.screens.GetReadyScreen
import dev.tidytv.app.ui.screens.HomeScreenStep
import dev.tidytv.app.ui.screens.ReviewScreen
import dev.tidytv.app.ui.screens.TidyingScreen
import dev.tidytv.app.ui.screens.UnsupportedScreen
import dev.tidytv.app.ui.screens.WelcomeScreen
import dev.tidytv.core.flow.Mode
import dev.tidytv.core.flow.Step

/** What the activity does for the app: things that need a Context or another app. */
class Host(
    val sendFeedback: suspend (dev.tidytv.app.data.FeedbackDraft, includeLog: Boolean) -> Boolean,
    val renderCard: (dev.tidytv.app.data.ShareStats) -> android.graphics.Bitmap,
    val shareCard: (dev.tidytv.app.data.ShareStats) -> Unit,
    val openStar: () -> Unit,
    val openUrl: (String) -> Unit = {},
    /** Share a screenshot of the TV (PNG bytes). */
    val shareScreenshot: (ByteArray) -> Unit = {},
    /** The rating card's memory and the store review; null in previews and tests. */
    val rating: dev.tidytv.app.data.RatingStore? = null,
    val requestReview: () -> Unit = {},
)

@Composable
fun TidyApp(s: UiState, vm: WizardViewModel, onShareLogs: () -> Unit, host: Host) {
    var feedback by remember { mutableStateOf<FeedbackRequest?>(null) }
    var card by remember { mutableStateOf<Pair<dev.tidytv.app.data.ShareStats, android.graphics.Bitmap>?>(null) }
    var showRating by remember { mutableStateOf(false) }
    s.remote.shot?.let { png -> androidx.compose.runtime.LaunchedEffect(png) { host.shareScreenshot(png); vm.screenshotShared() } }
    val actions = remember(s.tidy, s.done, s.tv, s.labels) {
        AppActions(
            rate = { showRating = true },
            feedback = { feedback = it },
            shareCard = { val st = shareStats(s); card = st to host.renderCard(st) },
            openUrl = host.openUrl,
        )
    }
    CompositionLocalProvider(LocalAppActions provides actions) {
        AnimatedContent(s.wizard.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { step ->
            Screen(step, s, vm, onShareLogs)
        }
    }
    // Ask right after a clean-up that really turned something off, once the success has landed.
    val goodMoment = s.wizard.step == dev.tidytv.core.flow.Step.DONE && s.tidy.results.values.any { it.isOff } &&
        s.done.restore is dev.tidytv.app.RestoreUi.Idle && s.problem == null
    androidx.compose.runtime.LaunchedEffect(goodMoment) {
        val store = host.rating
        if (goodMoment && store != null) {
            kotlinx.coroutines.delay(1_200)
            val busy = feedback != null || card != null || showRating
            if (dev.tidytv.app.data.RatingPolicy.shouldAsk(store.read(), System.currentTimeMillis(), goodMoment, busy)) {
                store.recordAsk()
                showRating = true
            }
        }
    }
    if (showRating) RatingPrompt(
        onRate = { stars ->
            showRating = false
            host.rating?.recordDone()
            if (stars >= HAPPY_STARS) host.requestReview()
            else feedback = FeedbackRequest(dev.tidytv.app.data.FeedbackKind.BUG, "rating", "[$stars/5 stars] ")
        },
        onLater = { showRating = false },
    )
    s.problem?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissProblem,
            confirmButton = { TextButton(vm::dismissProblem) { Text("OK") } },
            dismissButton = {
                TextButton({ vm.dismissProblem(); feedback = FeedbackRequest(dev.tidytv.app.data.FeedbackKind.BUG, "problem", "$msg\n\n") }) { Text("Report a problem") }
            },
            text = { Text(msg) },
        )
    }
    card?.let { (st, bmp) ->
        ShareCardDialog(
            bmp,
            onShare = { host.shareCard(st) },
            onStar = host.openStar,
            onTellUs = { card = null; feedback = FeedbackRequest(dev.tidytv.app.data.FeedbackKind.PRAISE, "share-card") },
            onDismiss = { card = null },
        )
    }
    feedback?.let { req -> FeedbackSheet(req, host.sendFeedback) { feedback = null } }
}

/** The numbers for the share card, from what this run actually did. */
fun shareStats(s: UiState): dev.tidytv.app.data.ShareStats {
    val labels = s.tidy.order.associate { it.pkg to it.label } + s.labels
    val off = s.tidy.results.filterValues { it.isOff }.keys
    // Apps people recognise first: a real icon, then a brand mark, then the rest.
    val apps = off.map { it to (labels[it] ?: dev.tidytv.core.lists.AppLabels.label(it)) }
        .sortedBy { (pkg, _) -> if (pkg in s.icons) 0 else if (dev.tidytv.core.lists.AppLabels.iconSlug(pkg) != null) 1 else 2 }
    return dev.tidytv.app.data.ShareStats(
        appsOff = off.size,
        tvName = s.tv?.model?.takeIf { it.isNotBlank() && !it.contains("demo", ignoreCase = true) } ?: s.tv?.title,
        freedMb = s.done.freedMb,
        newHome = s.done.newHome,
        apps = apps,
    )
}

/** One screen per wizard step. Also used directly by the screenshot tests. */
@Composable
fun Screen(step: Step, s: UiState, vm: WizardViewModel?, onShareLogs: () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalTvIcons provides s.icons) { StepScreen(step, s, vm, onShareLogs) }

@Composable
private fun StepScreen(step: Step, s: UiState, vm: WizardViewModel?, onShareLogs: () -> Unit) {
    when (step) {
        Step.WELCOME -> WelcomeScreen({ vm?.choose(it) }, { vm?.restoreTapped() }, s.update, { vm?.dismissUpdate() }, s.savedTvs, { vm?.connectSaved(it) }, { vm?.startRescue(it) }, s.online, { vm?.connectNew() })
        Step.CHECK -> CheckScreen(s.check, s.tv?.title, s.labels, { vm?.retidy() }, { vm?.rebootTest() }, { vm?.recheck() }, { vm?.toHub() }, { vm?.turnOnRemoteHelper(it) }, { vm?.repairHome() })
        Step.RESCUE -> RescueScreen(s.rescue, { vm?.stopRescue() }, { s.rescue.tv?.let { vm?.startRescue(it) } }, { vm?.stopRescue() })
        Step.HUB -> HubScreen(
            s.tv, s.done.records.offByUs().count { !dev.tidytv.core.lists.Launchers.isHomeApp(it) }, s.wizard.kind == dev.tidytv.core.flow.TvKind.FIRE_TV,
            { vm?.openTidy() }, { vm?.openRemote() }, { vm?.openFiles() }, { vm?.openHomeScreen() }, { vm?.openRestore() }, { vm?.back() }, { vm?.openCheck() }, { vm?.openSpace() }, { vm?.openAds() }, { vm?.openApps() }, s.hubFacts,
        )
        Step.REMOTE -> RemoteScreen(s.remote, s.tv?.title, { vm?.press(it) }, { vm?.type(it) }, { vm?.launchApp(it) }, { vm?.dismissRemoteMessage() }, { vm?.toHub() }, { vm?.togglePin(it) }, { p, d -> vm?.movePin(p, d) }, { vm?.takeScreenshot() })
        Step.SEND_FILES -> SendFilesScreen(s.files, s.tv?.title, { vm?.sendFiles(it) }, { vm?.cancelFile(it) }, { vm?.retryFile(it) }, { vm?.toHub() }, { vm?.setSendFolder(it) })
        Step.UNSUPPORTED -> UnsupportedScreen { vm?.back() }
        Step.GET_READY -> GetReadyScreen(s.wizard.kind, s.wizard.mode) { vm?.ready() }
        Step.CONNECT -> ConnectScreen(s.connect, { vm?.connect(it) }, onShareLogs)
        Step.REVIEW -> if (s.review.all != null) AllAppsScreen(
            s.review, s.labels, { vm?.toggleApp(it) }, { vm?.setUninstall(it) }, { vm?.startTidy() }, { vm?.closeAllApps() },
            { vm?.confirmPermanent() }, { vm?.cancelPermanent() },
        ) else ReviewScreen(
            s.review, { vm?.toggleApp(it) }, { g, on -> vm?.toggleGroup(g, on) }, { vm?.startTidy() },
            { vm?.retryBackup() }, { vm?.toDone() }, { vm?.showMoreApps() }, { vm?.openAllApps() }, { vm?.setUninstall(it) },
        )
        Step.APPS -> AppsScreen(s.apps, s.labels, s.tv?.title, { vm?.openApp(it) }, { vm?.closeApp() }, { vm?.changeApp(it, false) }, { vm?.changeApp(it, true) }, { vm?.toHub() })
        Step.ADS -> AdBlockScreen(s.ads, s.tv?.title, { p, ok -> vm?.blockAds(p, ok) }, { vm?.unblockAds() }, { vm?.cancelReplaceDns() }, { vm?.toHub() })
        Step.SPACE -> SpaceScreen(s.space, s.tv?.title, s.labels, { vm?.clearCaches() }, { vm?.toHub() })
        Step.TIDYING -> TidyingScreen(s.tidy, { vm?.toDone(); vm?.restoreEverything() }, { vm?.toDone() }, onShareLogs)
        Step.HOME_SCREEN -> HomeScreenStep(
            s.home, s.tidy.disabledCount, { vm?.toggleDock(it) }, { vm?.chooseHome(it) }, { vm?.installHome() }, { vm?.skipHome() },
            { a, eth -> vm?.homeAction(a, eth) }, { vm?.homeContinue() }, onShareLogs, { vm?.cancelPlayStore() },
            { vm?.openLook() }, { vm?.closeLook() }, { vm?.applyLook(it) }, { vm?.applyPhoto(it) }, { vm?.setSharp(it) },
        )
        Step.DONE -> DoneScreen(
            s.tv, s.done, s.tidy.disabledCount, s.wizard.mode == Mode.RESTORE, s.labels,
            { vm?.restoreEverything() }, { vm?.checkDebuggingOff() }, onShareLogs, { vm?.startOver() },
            { vm?.turnOn(it) }, { vm?.toHub() }, { vm?.toHub(); vm?.openCheck() },
        )
    }
}
