package app.add.engine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        BootTrace.start(this)
        super.onCreate(savedInstanceState)
        BootTrace.mark(this, "activity created")
        setContent {
            BootTrace.mark(this, "content set")
            EngineTheme {
                EngineScreen()
            }
        }
    }
}
