// MainActivity.kt
import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File
import java.net.URL

class HelloWorldActivity : AppCompatActivity() {
    private lateinit var downloadButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private var downloadID: Long = 0
    
    // Update these values with your GitHub repository information
    private val GITHUB_USER = "your-username"
    private val GITHUB_REPO = "your-repo"
    private val CURRENT_VERSION = "1.0.0" // Your app's current version

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        downloadButton = findViewById(R.id.downloadButton)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        checkForUpdates()

        downloadButton.setOnClickListener {
            if (checkPermissions()) {
                startDownload()
            }
        }

        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
                return false
            }
        }
        return true
    }

    private fun checkForUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://api.github.com/repos/$GITHUB_USER/$GITHUB_REPO/releases/latest"
                val response = URL(url).readText()
                val latestVersion = JSONArray(response).getJSONObject(0).getString("tag_name")

                if (latestVersion != CURRENT_VERSION) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestVersion)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateDialog(newVersion: String) {
        AlertDialog.Builder(this)
            .setTitle("อัพเดทใหม่")
            .setMessage("มีเวอร์ชั่นใหม่ $newVersion พร้อมให้ดาวน์โหลด")
            .setPositiveButton("ดาวน์โหลด") { _, _ ->
                if (checkPermissions()) {
                    startDownload()
                }
            }
            .setNegativeButton("ภายหลัง", null)
            .show()
    }

    private fun startDownload() {
        val url = "https://github.com/$GITHUB_USER/$GITHUB_REPO/releases/latest/download/app-release.apk"
        val fileName = "app-update.apk"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("กำลังดาวน์โหลด APK")
            .setDescription("กำลังดาวน์โหลดแอปพลิเคชัน")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadID = downloadManager.enqueue(request)

        // Start progress tracking
        trackProgress()
    }

    private fun trackProgress() {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        CoroutineScope(Dispatchers.IO).launch {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadID)
                val cursor = downloadManager.query(query)
                
                cursor.moveToFirst()
                val bytesDownloaded = cursor.getLong(
                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val bytesTotal = cursor.getLong(
                    cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                
                if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                    downloading = false
                }
                
                val progress = ((bytesDownloaded * 100L) / bytesTotal).toInt()
                withContext(Dispatchers.Main) {
                    progressBar.progress = progress
                    progressText.text = "$progress%"
                }
                
                cursor.close()
                delay(100)
            }
        }
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadID) {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "app-update.apk"
                )
                
                installAPK(file)
            }
        }
    }

    private fun installAPK(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            Uri.fromFile(file),
            "application/vnd.android.package-archive"
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }
}
