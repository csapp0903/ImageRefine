package com.imagerefine.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.imagerefine.app.ui.screens.EditScreen
import com.imagerefine.app.ui.screens.FilterListScreen
import com.imagerefine.app.ui.screens.HomeScreen
import com.imagerefine.app.ui.theme.ImageRefineTheme
import com.imagerefine.app.viewmodel.EditViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ImageRefineTheme {
                val navController = rememberNavController()
                val editViewModel: EditViewModel = viewModel()

                // 收集 Toast 消息
                LaunchedEffect(Unit) {
                    editViewModel.uiMessage.collectLatest { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        HomeScreen(
                            onImageSelected = { uri ->
                                editViewModel.loadImage(uri)
                                navController.navigate("edit")
                            },
                            onNavigateToFilters = {
                                navController.navigate("filters")
                            }
                        )
                    }

                    composable("edit") {
                        val originalBitmap by editViewModel.originalBitmap.collectAsStateWithLifecycle()
                        val processedBitmap by editViewModel.processedBitmap.collectAsStateWithLifecycle()
                        val parameters by editViewModel.parameters.collectAsStateWithLifecycle()
                        val filters by editViewModel.allFilters.collectAsStateWithLifecycle()
                        val showOriginal by editViewModel.showOriginal.collectAsStateWithLifecycle()
                        val isProcessing by editViewModel.isProcessing.collectAsStateWithLifecycle()
                        val isAiProcessing by editViewModel.isAiProcessing.collectAsStateWithLifecycle()
                        val aiStatusText by editViewModel.aiStatusText.collectAsStateWithLifecycle()
                        val beautyOptions by editViewModel.beautyOptions.collectAsStateWithLifecycle()
                        val serverUrl by editViewModel.serverUrl.collectAsStateWithLifecycle()

                        EditScreen(
                            originalBitmap = originalBitmap,
                            processedBitmap = processedBitmap,
                            parameters = parameters,
                            filters = filters,
                            showOriginal = showOriginal,
                            isProcessing = isProcessing,
                            isAiProcessing = isAiProcessing,
                            aiStatusText = aiStatusText,
                            beautyOptions = beautyOptions,
                            serverUrl = serverUrl,
                            onParameterChange = { editViewModel.updateParameter(it) },
                            onApplyFilter = { editViewModel.applyFilter(it) },
                            onSaveFilter = { editViewModel.saveCurrentAsFilter(it) },
                            onDeleteFilter = { editViewModel.deleteFilter(it) },
                            onExtractStyle = { editViewModel.extractStyle() },
                            onExtractFromReference = { editViewModel.extractStyleFromReference(it) },
                            onReset = { editViewModel.resetParameters() },
                            onSaveImage = { editViewModel.saveImage() },
                            onToggleOriginal = { editViewModel.toggleShowOriginal(it) },
                            onBack = { navController.popBackStack() },
                            // AI callbacks
                            onAiAutoEnhance = { editViewModel.aiAutoEnhance() },
                            onAiStyleTransfer = { editViewModel.aiStyleTransfer(it) },
                            onAiRemoveBackground = { editViewModel.aiRemoveBackground() },
                            onAiCompositeBackground = { editViewModel.aiCompositeBackground(it) },
                            onAiBeautyFace = { editViewModel.aiBeautyFace() },
                            onUpdateBeautyOptions = { editViewModel.updateBeautyOptions(it) },
                            onUpdateServerUrl = { editViewModel.updateServerUrl(it) }
                        )
                    }

                    composable("filters") {
                        val filters by editViewModel.allFilters.collectAsStateWithLifecycle()

                        FilterListScreen(
                            filters = filters,
                            onDeleteFilter = { editViewModel.deleteFilter(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
