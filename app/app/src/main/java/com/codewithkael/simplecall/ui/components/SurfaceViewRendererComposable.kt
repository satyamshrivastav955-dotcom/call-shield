package com.codewithkael.simplecall.ui.components

import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

@Composable
fun SurfaceViewRendererComposable(
    modifier: Modifier = Modifier,
    onSurfaceReady: (SurfaceViewRenderer) -> Unit
) {
    // Creating a card with a shadow, title, and surface view renderer
    Card(
        modifier = modifier
            .padding(8.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // SurfaceViewRenderer
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        addView(SurfaceViewRenderer(ctx).also {
                            onSurfaceReady.invoke(it)
                        })
                    }
                }
            )
        }
    }
}
