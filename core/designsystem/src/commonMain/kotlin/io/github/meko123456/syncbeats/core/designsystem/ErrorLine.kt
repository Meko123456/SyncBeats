package io.github.meko123456.syncbeats.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A screen's error, as a sentence from [ErrorCopy], in the error colour, with Dismiss under it
 * when the screen has a way to clear it.
 *
 * It is always composed: empty and zero-height while there is nothing to say. A screen reader
 * announces a *change* on a node it already knows (the text going from nothing to the sentence),
 * which is what the polite live region asks for; a `state.error?.let { Text(...) }` makes a new
 * node instead, and a node that appears is not reliably announced at all. The [gapAbove] is
 * part of the line for the same reason: a zero-height child still gets its spacer, so the
 * spacing moves with the words and the screen without an error is pixel-identical to before.
 */
@Composable
fun ErrorLine(
    error: String?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    gapAbove: Dp = 0.dp,
) {
    Column(modifier) {
        Text(
            text = error ?: "",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .then(if (error == null) Modifier.height(0.dp) else Modifier.padding(top = gapAbove))
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (error != null && onDismiss != null) {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
