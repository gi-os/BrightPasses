package com.lightpass.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.rememberKeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {
    override val viewModelClass: Class<HomeViewModel> = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel {
        val database = lightContext.buildDatabase(PassDatabase::class.java, "light-pass.db")
        return HomeViewModel(PassRepository(database.passDao(), lightContext), database)
    }

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val passes by viewModel.passes.collectAsState()
        val status by viewModel.status.collectAsState()
        val repository = viewModel.repository

        LightTheme(colors = colors) {
            Column(
                modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = { navigateTo({ SettingsScreen(it, repository) }) },
                        contentDescription = "Settings",
                    ),
                    center = LightTopBarCenter.Text("Passes"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.ADD,
                        onClick = { viewModel.importFromInbox() },
                        contentDescription = "Import from drop folder",
                    ),
                )
                status?.let { LightText(it, variant = LightTextVariant.Caption) }
                PassList(
                    passes = passes,
                    emptyMessage = "No passes yet.\n\nDrop images into the import folder over USB, then tap +.\nSet your Anthropic key in Settings to auto-title them.",
                    onOpen = { pass -> navigateTo({ ViewerScreen(it, repository, pass.id) }) },
                    modifier = Modifier,
                )
            }
        }
    }
}

class ViewerScreen(
    sealedActivity: SealedLightActivity,
    private val repository: PassRepository,
    private val passId: String,
) : LightScreen<Unit, ViewerViewModel>(sealedActivity) {
    override val viewModelClass: Class<ViewerViewModel> = ViewerViewModel::class.java
    override fun createViewModel() = ViewerViewModel(repository, passId)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val pass by viewModel.pass.collectAsState()

        LightTheme(colors = colors) {
            Column(modifier = Modifier.fillMaxSize().background(LightThemeTokens.colors.background)) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(pass?.title ?: "Pass"),
                )
                pass?.let { FullscreenPass(it.imagePath, Modifier) }
            }
        }
    }
}

class SettingsScreen(
    sealedActivity: SealedLightActivity,
    private val repository: PassRepository,
) : LightScreen<Unit, SettingsViewModel>(sealedActivity) {
    override val viewModelClass: Class<SettingsViewModel> = SettingsViewModel::class.java
    override fun createViewModel() = SettingsViewModel(repository)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        val input = key(state.inputSession) { rememberTextFieldState(state.draft) }
        val keyboard = rememberKeyboardOptions()

        LightTheme(colors = colors) {
            LightTextInputEditor(
                title = "Anthropic API key",
                editorKey = state.inputSession,
                keyboardOptionsFlow = keyboard,
                state = input,
                onSubmit = { raw -> viewModel.save(raw) { goBack() } },
                onBack = { goBack() },
                submitIcon = LightIcons.ACCEPT,
                showBackButton = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
