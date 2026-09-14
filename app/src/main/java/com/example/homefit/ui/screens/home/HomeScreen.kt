package com.example.homefit.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.homefit.model.WorkoutSession
import java.text.DateFormat
import java.util.Date

/**
 * P2.7 Home screen.
 *
 * Stateless: [activeSession] and both callbacks come from above
 * (`HomeFitNavDisplay` collects the `HomeViewModel` state). A null
 * [activeSession] renders the Start CTA; a non-null one renders Resume for
 * the existing session id. Resume never creates a session.
 */
@Composable
fun HomeScreen(
    activeSession: WorkoutSession?,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    startErrorMessage: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Твой прогресс начинается здесь.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (startErrorMessage != null) {
            Text(
                text = startErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (activeSession == null) {
            Button(onClick = onStartWorkout) {
                Text("Начать тренировку")
            }
        } else {
            Text(
                text = activeSession.programName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatStartedAt(activeSession.startedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onResumeWorkout) {
                Text("Продолжить тренировку")
            }
        }
    }
}

private fun formatStartedAt(startedAt: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(startedAt))
