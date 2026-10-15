package com.nhdeveloper.roomdraft.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nhdeveloper.roomdraft.ui.editor.EditorScreen
import com.nhdeveloper.roomdraft.ui.home.HomeScreen
import com.nhdeveloper.roomdraft.ui.project.CreateProjectScreen
import com.nhdeveloper.roomdraft.ui.project.ProjectScreen
import com.nhdeveloper.roomdraft.ui.settings.SettingsScreen
import com.nhdeveloper.roomdraft.ui.splash.SplashScreen
import com.nhdeveloper.roomdraft.ui.templates.TemplatesScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val TEMPLATES = "templates"
    const val SETTINGS = "settings"
    const val CREATE = "create?templateId={templateId}"
    const val PROJECT = "project/{projectId}"
    const val EDITOR = "editor/{projectId}"

    fun create(templateId: String? = null): String =
        if (templateId == null) "create" else "create?templateId=$templateId"

    fun project(id: String): String = "project/$id"
    fun editor(id: String): String = "editor/$id"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val goTab: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    val openEditor: (String) -> Unit = { id -> nav.navigate(Routes.editor(id)) }
    val openDetails: (String) -> Unit = { id -> nav.navigate(Routes.project(id)) }
    val newProject: (String?) -> Unit = { templateId -> nav.navigate(Routes.create(templateId)) }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onGetStarted = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    nav.navigate(Routes.create())
                },
                onOpenExisting = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNewProject = { newProject(null) },
                onOpenProject = openEditor,
                onOpenDetails = openDetails,
                onNavigate = goTab
            )
        }
        composable(Routes.TEMPLATES) {
            TemplatesScreen(onUseTemplate = { id -> newProject(id) }, onNavigate = goTab)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigate = goTab)
        }
        composable(
            route = Routes.CREATE,
            arguments = listOf(navArgument("templateId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { entry ->
            CreateProjectScreen(
                templateId = entry.arguments?.getString("templateId"),
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.navigate(Routes.editor(id)) { popUpTo(Routes.CREATE) { inclusive = true } }
                }
            )
        }
        composable(
            route = Routes.PROJECT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("projectId").orEmpty()
            ProjectScreen(
                projectId = id,
                onBack = { nav.popBackStack() },
                onOpenEditor = { openEditor(id) }
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { entry ->
            EditorScreen(
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                onBack = { nav.popBackStack() }
            )
        }
    }
}
