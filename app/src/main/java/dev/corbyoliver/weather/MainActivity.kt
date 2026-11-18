package dev.corbyoliver.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.corbyoliver.weather.sample.SampleScenario
import dev.corbyoliver.weather.ui.home.HomeScreen
import dev.corbyoliver.weather.ui.home.HomeSource
import dev.corbyoliver.weather.ui.home.HomeViewModel
import dev.corbyoliver.weather.ui.theme.WeatherTheme
import dev.corbyoliver.weather.ui.widget.AlertWidgetReceiver
import dev.corbyoliver.weather.ui.widget.RainTimingWidgetReceiver
import dev.corbyoliver.weather.ui.widget.TempRainWidgetReceiver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark app: light status and navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Test builds only (-PtestHooks): `--es scenario RAIN` (etc.) shows sample data for one frog look.
        val scenario = if (!BuildConfig.TEST_HOOKS) null else intent.getStringExtra("scenario")
            ?.let { name -> SampleScenario.entries.firstOrNull { it.name == name } }
        if (BuildConfig.TEST_HOOKS) intent.getStringExtra("pinWidget")?.let { requestPinWidget(it) }
        val source = if (scenario != null) HomeSource.Sample(scenario)
        else HomeSource.Live((application as WeatherApp).repository)
        setContent {
            WeatherTheme {
                val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(source) } })
                val state by vm.state.collectAsStateWithLifecycle()
                LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }
                HomeScreen(state, onSelectDay = vm::selectDay)
            }
        }
    }

    /**
     * Test builds only (-PtestHooks): `--es pinWidget temp|alert|rain` asks
     * the launcher to add that widget (Android shows a confirm dialog).
     */
    private fun requestPinWidget(which: String) {
        val receiver = when (which) {
            "temp" -> TempRainWidgetReceiver::class.java
            "alert" -> AlertWidgetReceiver::class.java
            "rain" -> RainTimingWidgetReceiver::class.java
            else -> return
        }
        val manager = getSystemService(android.appwidget.AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(android.content.ComponentName(this, receiver), null, null)
        }
    }
}
