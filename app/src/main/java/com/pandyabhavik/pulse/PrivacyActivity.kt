package com.pandyabhavik.pulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pandyabhavik.pulse.ui.C
import com.pandyabhavik.pulse.ui.PulseTheme

/** Opened by Health Connect ("Why does this app need access?"). */
class PrivacyActivity : ComponentActivity() {
    private val sections = listOf(
        R.string.privacy_reads_title to R.string.privacy_reads,
        R.string.privacy_writes_title to R.string.privacy_writes,
        R.string.privacy_where_title to R.string.privacy_where,
        R.string.privacy_background_title to R.string.privacy_background,
        R.string.privacy_control_title to R.string.privacy_control,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so the bar icons are always light, whatever the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            PulseTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(C.bg)
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .widthIn(max = 640.dp),
                ) {
                    Text(stringResource(R.string.privacy_heading), color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.privacy_summary), color = C.mute, fontSize = 15.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(20.dp))
                    sections.forEach { (title, body) ->
                        Text(stringResource(title), color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(body), color = C.mute, fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
