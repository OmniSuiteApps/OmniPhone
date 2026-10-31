package app.telephone

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Numéro reçu d'une autre appli (lien tel:, bouton « Appeler ») ; [seq] relance l'affichage du clavier même si le numéro est identique. */
data class DialRequest(val number: String, val seq: Int)

class MainActivity : ComponentActivity() {
    private val roleManager by lazy { getSystemService(RoleManager::class.java) }

    /** Incrémenté à chaque retour sur l'appli : relit le journal, les contacts et les autorisations. */
    private var refresh by mutableIntStateOf(0)
    private var dialRequest by mutableStateOf<DialRequest?>(null)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    private val role = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { requestPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        L10n.apply(resources.configuration.locales[0])
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        handle(intent)
        if (savedInstanceState == null) {
            if (isDefault()) requestPermissions() else requestRole()
        }
        setContent {
            TelephoneTheme {
                MainScreen(
                    refresh = refresh,
                    dialRequest = dialRequest,
                    isDefault = isDefault(),
                    onRequestRole = ::requestRole,
                    onRequestPermissions = ::requestPermissions,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    private fun handle(intent: Intent?) {
        if (intent?.action != Intent.ACTION_DIAL && intent?.action != Intent.ACTION_VIEW) return
        val number = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        dialRequest = DialRequest(number, (dialRequest?.seq ?: 0) + 1)
    }

    private fun isDefault() = refresh >= 0 && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)

    private fun requestRole() = role.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))

    private fun requestPermissions() = permissions.launch(PERMISSIONS)

    companion object {
        val PERMISSIONS = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}
