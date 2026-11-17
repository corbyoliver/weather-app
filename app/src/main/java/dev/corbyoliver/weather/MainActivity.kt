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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark app: light status and navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Debug builds only: `--es scenario RAIN` (etc.) shows sample data for one frog look.
        val scenario = if (!BuildConfig.DEBUG) null else intent.getStringExtra("scenario")
            ?.let { name -> SampleScenario.entries.firstOrNull { it.name == name } }
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
}
