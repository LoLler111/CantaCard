package dev.cantacard.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import dev.cantacard.app

/** Riceve dal [PackageInstaller] l'esito dell'installazione di un aggiornamento. */
class UpdateStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // La schermata di conferma di Android (e, la prima volta, il permesso di installare app).
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            if (confirm != null) {
                try {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Conferma dell'installazione non aperta", e)
                }
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            Log.w(TAG, "Installazione non riuscita ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
        context.app.updater.onInstallStatus(status)
    }

    companion object {
        private const val TAG = "UpdateStatusReceiver"

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, UpdateStatusReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                // Android aggiunge all'intent l'esito: deve restare modificabile.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
    }
}
