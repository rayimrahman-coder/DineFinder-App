package com.example.dinefinder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class NavigationSheetPosition { Hidden, Expanded }

/** Hides navigation controls without stopping the camera, GPS or voice guidance. */
@Composable
internal fun NavigationControlsSheet(cameraEnabled: Boolean, content: @Composable () -> Unit) {
    val state = rememberSaveable(saver = AnchoredDraggableState.Saver<NavigationSheetPosition>()) {
        AnchoredDraggableState(NavigationSheetPosition.Hidden)
    }
    // Turning the camera back on restores an unobstructed preview.
    LaunchedEffect(cameraEnabled) {
        if (cameraEnabled && state.anchors.size > 0) state.animateTo(NavigationSheetPosition.Hidden, tween(240))
    }
    if (!cameraEnabled) {
        content()
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val handleHeight = minOf(64.dp * density.fontScale.coerceAtLeast(1f), maxHeight * .65f)
        val hiddenTop = with(density) { (maxHeight - handleHeight).toPx() }.coerceAtLeast(1f)
        val anchors = remember(hiddenTop) { DraggableAnchors {
            NavigationSheetPosition.Expanded at 0f
            NavigationSheetPosition.Hidden at hiddenTop
        } }
        SideEffect { state.updateAnchors(anchors, state.targetValue) }
        val scope = rememberCoroutineScope()
        fun move(expand: Boolean) { scope.launch {
            state.animateTo(if (expand) NavigationSheetPosition.Expanded else NavigationSheetPosition.Hidden, tween(240))
        } }
        val top = (state.offset.takeIf { it.isFinite() } ?: hiddenTop).coerceIn(0f, hiddenTop)
        val hidden = top >= hiddenTop - 1f
        val nestedScroll = remember(state) { object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y < 0)
                    Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                state.settle(tween(240))
                return Velocity(0f, available.y)
            }
        } }
        BackHandler(enabled = !hidden) { move(false) }
        Surface(Modifier.offset { IntOffset(0, top.roundToInt()) }.fillMaxWidth()
            .height(with(density) { (constraints.maxHeight - top).toDp() })
            .testTag("navigation_controls_sheet").semantics {
                stateDescription = if (hidden) "Hidden" else "Expanded"
                customActions = listOf(
                    CustomAccessibilityAction("Show navigation controls") { move(true); true },
                    CustomAccessibilityAction("Hide navigation controls") { move(false); true })
            }, color = Color.White, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 4.dp) {
            Column {
                Column(Modifier.fillMaxWidth().heightIn(min = handleHeight)
                    .anchoredDraggable(state, Orientation.Vertical,
                        flingBehavior = AnchoredDraggableDefaults.flingBehavior(state))
                    .testTag("navigation_sheet_handle"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 7.dp).size(34.dp, 4.dp).background(DiscoveryMuted.copy(alpha = .4f), CircleShape))
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (hidden) "Swipe up for navigation" else "Navigation controls", color = DiscoveryGreen,
                            fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { move(hidden) }) {
                            Icon(if (hidden) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                if (hidden) "Show navigation controls" else "Hide navigation controls", tint = DiscoveryGreen)
                        }
                    }
                }
                if (!hidden) Box(Modifier.weight(1f).nestedScroll(nestedScroll)) { content() }
            }
        }
    }
}
