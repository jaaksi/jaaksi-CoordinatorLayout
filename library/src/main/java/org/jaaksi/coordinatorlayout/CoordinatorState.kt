package org.jaaksi.coordinatorlayout

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

@Composable
fun rememberCoordinatorState(): CoordinatorState {
    return rememberSaveable(saver = CoordinatorState.Saver) { CoordinatorState() }
}

@Stable
class CoordinatorState {
    // 已折叠的高度
    var collapsedHeight: Float by mutableFloatStateOf(0f)
        private set

    var isFullyCollapsed by mutableStateOf(false)
        private set

    private var _maxCollapsableHeight = mutableFloatStateOf(Float.MAX_VALUE)

    // 最大可折叠高度
    var maxCollapsableHeight: Float
        get() = _maxCollapsableHeight.floatValue
        internal set(value) {
            if (value.isNaN()) return
            _maxCollapsableHeight.floatValue = value
            Snapshot.withoutReadObservation {
                if (collapsedHeight >= value) {
                    collapsedHeight = value
                    isFullyCollapsed = true
                } else if (isFullyCollapsed){
                    collapsedHeight = value
                }
            }

        }

    val scrollableState = ScrollableState {
        val newValue = (collapsedHeight - it).coerceIn(0f, maxCollapsableHeight)
        val consumed = collapsedHeight - newValue
        collapsedHeight = newValue
        isFullyCollapsed = newValue == maxCollapsableHeight
        consumed
    }

    // animTo 完全折叠状态
    suspend fun animateToCollapsed(
        animationSpec: AnimationSpec<Float> = tween(
            100,
            easing = LinearEasing
        )
    ) {
        animateScrollBy(collapsedHeight - maxCollapsableHeight, animationSpec)
    }

    /**
     * @param value 折叠的高度
     */
    suspend fun animateTo(
        value: Float,
        animationSpec: AnimationSpec<Float> = tween(100, easing = LinearEasing)
    ) {
        animateScrollBy((value - maxCollapsableHeight).coerceIn(0f, maxCollapsableHeight), animationSpec)
    }

    private suspend fun animateScrollBy(
        value: Float,
        animationSpec: AnimationSpec<Float> = tween(100, easing = LinearEasing)
    ) {
        scrollableState.animateScrollBy(value, animationSpec)
    }

    private fun consume(available: Offset): Offset {
        val consumedY = scrollableState.dispatchRawDelta(available.y)
        return available.copy(y = consumedY)
    }


    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 水平方向不消耗
            if (available.x != 0f) return Offset.Zero
            // 向上滑动，如果没有达到最大可折叠高度，则自己先消耗
            if (available.y < 0 && collapsedHeight < maxCollapsableHeight) {
                return consume(available)
            }

            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y > 0) {
                return consume(available)
            }
            return Offset.Zero
        }
    }

    companion object {
        val Saver: Saver<CoordinatorState, *> = Saver(
            save = { listOf(it.collapsedHeight, it.maxCollapsableHeight) },
            restore = {
                CoordinatorState().apply {
                    collapsedHeight = it[0]
                    maxCollapsableHeight = it[1]
                    isFullyCollapsed = collapsedHeight >= maxCollapsableHeight
                }
            }
        )
    }
}