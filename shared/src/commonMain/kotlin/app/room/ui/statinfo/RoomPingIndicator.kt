package app.room.ui.statinfo

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.NetworkWifi1Bar
import androidx.compose.material.icons.outlined.NetworkWifi2Bar
import androidx.compose.material.icons.outlined.NetworkWifi3Bar
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.SignalWifiConnectedNoInternet4
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.protocol.models.PingService

sealed class PingLevel(
    val icon: ImageVector,
    val tint: Color
) {
    data object NoInternet : PingLevel(Icons.Outlined.SignalWifiConnectedNoInternet4, Color.Gray)
    data object Excellent : PingLevel(Icons.Outlined.SignalWifi4Bar, Color.Green)
    data object Good : PingLevel(Icons.Outlined.NetworkWifi, Color.Yellow)
    data object Fair : PingLevel(Icons.Outlined.NetworkWifi3Bar, Color(255, 176, 66))
    data object Poor : PingLevel(Icons.Outlined.NetworkWifi2Bar, Color(181, 80, 25))
    data object Terrible : PingLevel(Icons.Outlined.NetworkWifi1Bar, Color.Red)
    companion object {
        fun from(state: PingService.Companion.ConnectionState): PingLevel = when (state) {
            is PingService.Companion.ConnectionState.Disconnected -> NoInternet
            is PingService.Companion.ConnectionState.Connected -> {
                when (state.pingMs) {
                    in 0..125 -> Excellent
                    in 126..189 -> Good
                    in 190..239 -> Fair
                    in 240..299 -> Poor
                    else -> Terrible
                }
            }
        }
    }
}

@Composable
fun PingIndicator(state: PingService.Companion.ConnectionState) {
    val pingLevel = PingLevel.from(state)
    Icon(
        imageVector = pingLevel.icon,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = pingLevel.tint
    )
}