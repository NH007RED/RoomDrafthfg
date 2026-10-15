package com.nhdeveloper.roomdraft.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onGetStarted: () -> Unit, onOpenExisting: () -> Unit) {
    val draw = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) {
        delay(150)
        reveal.animateTo(1f, tween(700))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF062B19), Color(0xFF0B6B38), Color(0xFF0E9F4F))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            AnimatedLogo(
                progress = draw.value,
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        val s = 0.8f + 0.2f * draw.value
                        scaleX = s
                        scaleY = s
                        alpha = (draw.value * 3f).coerceIn(0f, 1f)
                    }
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "RoomDraft",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer { alpha = reveal.value }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Plan • Arrange • Design",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                modifier = Modifier.graphicsLayer { alpha = reveal.value }
            )
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.graphicsLayer { alpha = reveal.value },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0A6B36))
                ) { Text("Get Started", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenExisting) {
                    Text("Open Existing Project", color = Color.White)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnimatedLogo(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = CornerRadius(s * 0.26f))
        val house = Path().apply {
            moveTo(s * 0.5f, s * 0.2f)
            lineTo(s * 0.8f, s * 0.46f)
            lineTo(s * 0.8f, s * 0.78f)
            lineTo(s * 0.2f, s * 0.78f)
            lineTo(s * 0.2f, s * 0.46f)
            close()
        }
        val measure = PathMeasure()
        measure.setPath(house, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * progress, segment, true)
        drawPath(
            segment,
            Color.White,
            style = Stroke(width = s * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        val a = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)
        drawRoundRect(
            Color.White.copy(alpha = a),
            topLeft = Offset(s * 0.32f, s * 0.48f),
            size = Size(s * 0.36f, s * 0.14f),
            cornerRadius = CornerRadius(s * 0.04f)
        )
        drawRoundRect(
            Color.White.copy(alpha = a),
            topLeft = Offset(s * 0.28f, s * 0.58f),
            size = Size(s * 0.44f, s * 0.1f),
            cornerRadius = CornerRadius(s * 0.04f)
        )
    }
}
