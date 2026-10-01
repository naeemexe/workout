package work.lockedinlabs.tracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.viewmodel.compose.viewModel
import work.lockedinlabs.tracker.ui.home.HomeScreen
import work.lockedinlabs.tracker.ui.home.HomeViewModel
import work.lockedinlabs.tracker.ui.log.LogScreen
import work.lockedinlabs.tracker.ui.log.LogViewModel
import work.lockedinlabs.tracker.ui.plan.PlansScreen
import work.lockedinlabs.tracker.ui.plan.RuleScreen
import work.lockedinlabs.tracker.ui.plan.RulesScreen
import work.lockedinlabs.tracker.ui.more.ExerciseDetailScreen
import work.lockedinlabs.tracker.ui.more.ExercisesScreen
import work.lockedinlabs.tracker.ui.more.ExercisesViewModel
import work.lockedinlabs.tracker.ui.more.MoreScreen
import androidx.compose.material.icons.filled.Menu
import work.lockedinlabs.tracker.ui.plan.RulesViewModel
import work.lockedinlabs.tracker.ui.plan.PlanScreen
import work.lockedinlabs.tracker.ui.plan.PlanViewModel
import work.lockedinlabs.tracker.ui.plan.PresetPlanScreen
import work.lockedinlabs.tracker.pack.Level
import work.lockedinlabs.tracker.ui.profile.ProfileScreen
import work.lockedinlabs.tracker.ui.profile.ProfileViewModel
import work.lockedinlabs.tracker.ui.weight.WeightScreen
import work.lockedinlabs.tracker.ui.weight.WeightViewModel
import work.lockedinlabs.tracker.ui.theme.LockedInTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import work.lockedinlabs.tracker.data.ThemeMode
import work.lockedinlabs.tracker.ui.AccountDrawer
import work.lockedinlabs.tracker.ui.settings.SettingsScreen
import work.lockedinlabs.tracker.ui.settings.SupportScreen
import work.lockedinlabs.tracker.ui.onboarding.OnboardingScreen
import work.lockedinlabs.tracker.ui.onboarding.OnboardingViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as LockedInApp).settings
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status/nav bar icons: light icons on dark backgrounds and vice versa.
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            LockedInTheme(dark) {
                val onboarding: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)
                when (onboarding.needed) {
                    null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) // checking, a moment
                    true -> OnboardingScreen(onboarding)
                    false -> AppShell(mode, settings::setThemeMode)
                }
            }
        }
    }
}

private enum class Tab { HOME, LOG, MORE }

/** Screens inside the More tab. */
private enum class MoreRoute { MAIN, PLANS, PROGRESSION, EXERCISES }

/** Screens reached from the avatar's side panel (shown in place of Home). */
private enum class HomeRoute { MAIN, PROFILE, SETTINGS, SUPPORT, WEIGHT }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppShell(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var homeRoute by rememberSaveable { mutableStateOf(HomeRoute.MAIN) }
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val homeVm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val logVm: LogViewModel = viewModel(factory = LogViewModel.Factory)
    val planVm: PlanViewModel = viewModel(factory = PlanViewModel.Factory)
    val rulesVm: RulesViewModel = viewModel(factory = RulesViewModel.Factory)
    val exercisesVm: ExercisesViewModel = viewModel(factory = ExercisesViewModel.Factory)
    var moreRoute by rememberSaveable { mutableStateOf(MoreRoute.MAIN) }
    /** Back to the More menu, closing any editor. */
    fun moreHome() { planVm.closeEditor(); planVm.closePreset(); rulesVm.close(); exercisesVm.close(); moreRoute = MoreRoute.MAIN }
    val profileVm: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
    val weightVm: WeightViewModel = viewModel(factory = WeightViewModel.Factory)
    // The weight page opens from Home or Profile; Back returns to whichever it came from.
    var weightReturn by rememberSaveable { mutableStateOf(HomeRoute.MAIN) }
    fun openWeight(from: HomeRoute) { weightReturn = from; homeRoute = HomeRoute.WEIGHT }

    fun openFromDrawer(route: HomeRoute) {
        homeRoute = route
        tab = Tab.HOME
        scope.launch { drawer.close() }
    }

    BackHandler(enabled = drawer.isOpen || tab != Tab.HOME || homeRoute != HomeRoute.MAIN) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            tab == Tab.HOME -> homeRoute = if (homeRoute == HomeRoute.WEIGHT) weightReturn else HomeRoute.MAIN
            tab == Tab.MORE && rulesVm.editingId != null -> rulesVm.close()
            tab == Tab.MORE && planVm.editingId != null -> planVm.closeEditor()
            tab == Tab.MORE && planVm.preview != null -> planVm.closePreset()
            tab == Tab.MORE && exercisesVm.selected != null -> exercisesVm.close()
            tab == Tab.MORE && moreRoute != MoreRoute.MAIN -> moreRoute = MoreRoute.MAIN
            else -> tab = Tab.HOME
        }
    }

    // The avatar sits top-right, so the panel slides in from the right: lay the drawer out right-to-left,
    // and switch its contents back to left-to-right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen, // no edge-swipe, so it never fights the chart's drag
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AccountDrawer(
                        profile = profileVm.profile.takeIf { profileVm.loaded },
                        account = profileVm.account,
                        onProfile = { openFromDrawer(HomeRoute.PROFILE) },
                        onSettings = { openFromDrawer(HomeRoute.SETTINGS) },
                        onSupport = { openFromDrawer(HomeRoute.SUPPORT) },
                    )
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            NavigationBarItem(
                                selected = tab == Tab.HOME,
                                onClick = { if (tab == Tab.HOME) homeRoute = HomeRoute.MAIN; tab = Tab.HOME },
                                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                label = { Text("Home") },
                            )
                            NavigationBarItem(
                                selected = tab == Tab.LOG,
                                onClick = { tab = Tab.LOG },
                                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                label = { Text("Log") },
                            )
                            NavigationBarItem(
                                selected = tab == Tab.MORE,
                                onClick = { if (tab == Tab.MORE) moreHome(); tab = Tab.MORE },
                                icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                                label = { Text("More") },
                            )
                        }
                    },
                ) { padding ->
                    // Consume the bars' insets so the Log screen's imePadding() doesn't double-count them.
                    val focusManager = LocalFocusManager.current
                    // Tapping anywhere that isn't a field or button closes the keyboard and unfocuses the field.
                    val content = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                    val back = { homeRoute = HomeRoute.MAIN }
                    when (tab) {
                        Tab.HOME -> when (homeRoute) {
                            HomeRoute.MAIN -> HomeScreen(
                                viewModel = homeVm,
                                onLogClick = { tab = Tab.LOG },
                                onStepUpClick = { name -> logVm.logToday(name); tab = Tab.LOG },
                                profile = profileVm.profile.takeIf { profileVm.loaded },
                                onProfileClick = { scope.launch { drawer.open() } },
                                weightLbs = weightVm.latest?.lbs,
                                weightNote = weightVm.tileNote,
                                onOpenWeight = { openWeight(HomeRoute.MAIN) },
                                calendarTags = logVm.loggedDayTags,
                                onDayClick = { d -> logVm.onDateChange(d); tab = Tab.LOG },
                                modifier = content,
                            )
                            HomeRoute.PROFILE -> ProfileScreen(
                                profileVm,
                                weightLbs = weightVm.latest?.lbs,
                                onOpenWeight = { openWeight(HomeRoute.PROFILE) },
                                onBack = back,
                                modifier = content,
                            )
                            HomeRoute.WEIGHT -> WeightScreen(weightVm, snackbar, onBack = { homeRoute = weightReturn }, modifier = content)
                            HomeRoute.SETTINGS -> SettingsScreen(themeMode, onThemeMode, onBack = back, modifier = content)
                            HomeRoute.SUPPORT -> SupportScreen(onBack = back, modifier = content)
                        }
                        Tab.LOG -> LogScreen(logVm, snackbar, onOpenPlans = { moreHome(); moreRoute = MoreRoute.PLANS; tab = Tab.MORE }, modifier = content)
                        Tab.MORE -> when {
                            rulesVm.editingId != null -> RuleScreen(rulesVm, onBack = rulesVm::close, modifier = content)
                            planVm.editingId != null -> PlanScreen(planVm, onBack = planVm::closeEditor, modifier = content)
                            planVm.preview != null -> planVm.preview?.let { p ->
                                PresetPlanScreen(p, onUse = { planVm.usePreset(p) }, onBack = planVm::closePreset, modifier = content)
                            }
                            exercisesVm.selected != null -> ExerciseDetailScreen(exercisesVm, onBack = exercisesVm::close, modifier = content)
                            moreRoute == MoreRoute.PLANS -> PlansScreen(
                                planVm,
                                level = Level.of(profileVm.profile.experience),
                                onBack = { moreRoute = MoreRoute.MAIN },
                                modifier = content,
                            )
                            moreRoute == MoreRoute.PROGRESSION -> RulesScreen(rulesVm, onBack = { moreRoute = MoreRoute.MAIN }, modifier = content)
                            moreRoute == MoreRoute.EXERCISES -> ExercisesScreen(exercisesVm, onBack = { moreRoute = MoreRoute.MAIN }, modifier = content)
                            else -> {
                                val active = planVm.allPlans.firstOrNull { it.plan.isActive }?.plan
                                val defaultRule = rulesVm.rules.firstOrNull { it.isDefault }
                                MoreScreen(
                                    sessionSummary = active?.let { "${it.displayName} · active" + if (planVm.allPlans.size > 1) " · ${planVm.allPlans.size} plans" else "" }
                                        ?: if (planVm.allPlans.isEmpty()) "Set up your training rotation" else "No active plan",
                                    progressionSummary = (defaultRule?.let { "${it.displayName} is the default" } ?: "When to step up or down") +
                                        if (rulesVm.rules.size > 1) " · ${rulesVm.rules.size} plans" else "",
                                    exerciseSummary = "${exercisesVm.entries.size - exercisesVm.customCount} built-in" +
                                        if (exercisesVm.customCount > 0) " · ${exercisesVm.customCount} yours" else "",
                                    onSessionPlans = { moreRoute = MoreRoute.PLANS },
                                    onProgressionPlans = { moreRoute = MoreRoute.PROGRESSION },
                                    onExercises = { moreRoute = MoreRoute.EXERCISES },
                                    modifier = content,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
