package io.github.davidec00.cardstack

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.ThresholdConfig
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue

/**
 * Controller of the [draggableStack] modifier.
 *
 * @param clock The animation clock that will be used to drive the animations.
 * @param screenWidth The width of the screen used to calculate properties such as rotation and scale
 * @param animationSpec The default animation that will be used to animate swipes.
 */
class CardStackController(
    private val scope: CoroutineScope,
    val screenWidth: Float,
) {

    private val animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec
    private val swipeThresholdPercent = 0.5f

    /**
     * Threshold to start swiping
     */
    val activationThresholdX: Float = screenWidth * swipeThresholdPercent

    /**
     * The current position (in pixels) of the First Card.
     */
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)

    /**
     * The current rotation (in pixels) of the First Card.
     */
    val rotation = Animatable(0f)

    /**
     * The current scale factor (in pixels) of the Card before the first one displayed.
     */
    val scale = Animatable(0.8f)

    var onSwipeLeft: () -> Unit = {}
    var onSwipeRight: () -> Unit = {}


    fun swipeLeft() {
        scope.apply {
            launch {
                // animate just outside screen
                offsetX.animateTo(-1.2f * screenWidth, animationSpec)

                onSwipeLeft()

                // After the animation of swiping,
                // snap return back to Center to make it look like a cycle
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
                rotation.snapTo(0f)
                scale.snapTo(0.8f)
            }

            launch {
                scale.animateTo(1f, animationSpec)
            }
        }

    }

    fun swipeRight() {
        scope.apply {
            launch {
                // animate just outside screen
                offsetX.animateTo(1.2f * screenWidth, animationSpec)

                onSwipeRight()

                // After the animation of swiping,
                // snap return back to Center to make it look like a cycle
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
                scale.snapTo(0.8f)
                rotation.snapTo(0f)
            }

            launch {
                scale.animateTo(1f, animationSpec)
            }
        }

    }

    fun returnCenter() {
        scope.apply {
            launch {
                offsetX.animateTo(0f, animationSpec)
            }
            launch {
                offsetY.animateTo(0f, animationSpec)
            }
            launch {
                rotation.animateTo(0f, animationSpec)
            }
            launch {
                scale.animateTo(0.8f, animationSpec)
            }
        }
    }


}

/**
 * Create and [remember] a [CardStackController] with the default animation clock.
 */
@Composable
fun rememberCardStackController(): CardStackController {
    val scope = rememberCoroutineScope()
    val screenWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    return remember {
        CardStackController(
            scope = scope,
            screenWidth = screenWidth,
        )
    }
}

/**
 * Enable drag gestures between a set of predefined anchors defined in [controller].
 *
 * @param controller The controller of the [draggableStack].
 * @param thresholdConfig Specifies where the threshold between the predefined Anchors is. This is represented as a lambda
 * that takes two float and returns the threshold between them in the form of a [ThresholdConfig].
 * in order to swipe, even if the positional [thresholds] have not been reached.
 */
fun Modifier.draggableStack(
    controller: CardStackController
): Modifier = composed {
    val density = LocalDensity.current

    val scope = rememberCoroutineScope()
    val tracker = remember { VelocityTracker() }

    // The threshold (set in dp per second) that the end velocity has to exceed
    val velocityThreshold = remember { with(density) { 200.dp.toPx() } }

    Modifier.pointerInput(Unit) {

        detectDragGestures(
            onDragStart = {
                tracker.resetTracking()
            },

            onDragEnd = {
                val velocity = tracker.calculateVelocity().x.absoluteValue
                tracker.resetTracking()

                if (controller.offsetX.value <= 0f) {
                    // Swipe to left

                    // check drop position > activation threshold
                    if (controller.offsetX.value < -controller.activationThresholdX) controller.swipeLeft()
                    // else check drop velocity > threshold velocity
                    else if (velocity >= velocityThreshold) controller.swipeLeft()
                    // else return to center
                    else controller.returnCenter()
                } else {
                    // Swipe to right

                    // check drop position > activation threshold
                    if (controller.offsetX.value > controller.activationThresholdX) controller.swipeRight()
                    // else check drop velocity > threshold velocity
                    else if (velocity >= velocityThreshold) controller.swipeRight()
                    // else return to center
                    else controller.returnCenter()
                }
            },

            onDrag = { change, dragAmount ->
                scope.launch {
                    controller.offsetX.snapTo(controller.offsetX.value + dragAmount.x)
                    controller.offsetY.snapTo(controller.offsetY.value + dragAmount.y)

                    val targetRotation = normalize(
                        value = controller.offsetX.value,
                        inputMin = -controller.screenWidth,
                        inputMax = controller.screenWidth,
                        outputMin = -20f,
                        outputMax = 20f
                    )
                    controller.rotation.snapTo(targetRotation)

                    val targetScale = normalize(
                        value = abs(controller.offsetX.value),
                        inputMin = 0f,
                        inputMax = controller.activationThresholdX,
                        outputMin = 0.8f,
                        outputMax = 1.0f
                    )
                    controller.scale.snapTo(targetScale)
                }

                change.consumePositionChange()
                tracker.addPointerInputChange(change)
            }
        )
    }
}

/**
 * Min max normalization
 *
 * @param inputMin Minimum of the range
 * @param inputMax Maximum of the range
 * @param value Value to normalize in the given [min, max] range
 * @param outputMin Transform the normalized value with a particular start range
 * @param outputMax Transform the normalized value with a particular end range
 */
fun normalize(
    value: Float,
    inputMin: Float,
    inputMax: Float,
    outputMin: Float = 0f,
    outputMax: Float = 1f
): Float {
    require(outputMin < outputMax) { "inputMin is greater than inputMax" }

    val value = value.coerceIn(inputMin, inputMax)
    return (value - inputMin) / (inputMax - inputMin) * (outputMax - outputMin) + outputMin
}
