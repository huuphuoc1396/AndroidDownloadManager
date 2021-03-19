package com.androiddownloadmanager

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.androiddownloadmanager.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var viewDataBinding: ActivityMainBinding
    private var downloadManager: DownloadManager? = null
    private var downloadImageId: Long = -1
    private var trackingStatusThread: Thread? = null

    @Volatile
    private var isDownloadCompleted = false

    private var onCompleted: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isDownloadCompleted = true
            viewDataBinding.startDownloadButton.isEnabled = true
            viewDataBinding.cancelDownloadButton.isEnabled = false
            viewDataBinding.downloadStatusText.text = getStatusMessage(downloadImageId)
            Toast.makeText(context, R.string.toast_download_completed, Toast.LENGTH_LONG).show()
        }
    }

    private var onNotificationClicked: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Toast.makeText(context, R.string.toast_download_noti_clicked, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewDataBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        requestStoragePermissionGranted()

        // Instances of download manager
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        viewDataBinding.startDownloadButton.setOnClickListener {
            startDownload(Uri.parse("https://commonsware.com/misc/test.mp4"))
        }

        viewDataBinding.cancelDownloadButton.setOnClickListener {
            downloadManager?.remove(downloadImageId)
        }

        registerReceiver(onCompleted, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        registerReceiver(
            onNotificationClicked,
            IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onCompleted)
        unregisterReceiver(onNotificationClicked)
        trackingStatusThread?.interrupt()
    }

    private fun requestStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
            }
        }
    }

    private fun startDownload(uri: Uri) {
        isDownloadCompleted = false

        val request = DownloadManager.Request(uri)

        // Setting title of request
        request.setTitle("Android Download Manager")

        // Setting description of request
        request.setDescription("Android download using DownloadManager")

        // Set the local destination for the downloaded file to a path
        // within the application's external files directory
        request.setDestinationInExternalFilesDir(
            this@MainActivity,
            Environment.DIRECTORY_DOWNLOADS,
            "test.mp4"
        )
        // Enqueue download and save into referenceId
        downloadImageId = downloadManager?.enqueue(request) ?: -1
        startDownloadStatusTracking(downloadImageId)

        viewDataBinding.startDownloadButton.isEnabled = false
        viewDataBinding.cancelDownloadButton.isEnabled = true
    }

    private fun startDownloadStatusTracking(downloadImageId: Long) {
        trackingStatusThread = Thread {
            while (!isDownloadCompleted) {
                runOnUiThread {
                    viewDataBinding.downloadStatusText.text = getStatusMessage(downloadImageId)
                }
                Thread.sleep(TRACKING_STATUS_DELAY)
            }
        }
        trackingStatusThread?.start()
    }

    private fun getStatusMessage(downloadId: Long): String {

        val query = DownloadManager.Query()
        // set the query filter to our previously Enqueued download
        query.setFilterById(downloadId)

        // Query the download manager about downloads that have been requested.
        val cursor = downloadManager?.query(query)
        if (cursor?.moveToFirst() == true) {
            return downloadStatus(cursor)
        }
        return "NO_STATUS_INFO"
    }

    private fun downloadStatus(cursor: Cursor): String {

        // column for download  status
        val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val status = cursor.getInt(columnIndex)
        // column for reason code if the download failed or paused
        val columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
        val reason = cursor.getInt(columnReason)

        var statusText = ""
        var reasonText = ""

        when (status) {
            DownloadManager.STATUS_FAILED -> {
                statusText = "STATUS_FAILED"
                reasonText = when (reason) {
                    DownloadManager.ERROR_CANNOT_RESUME -> "ERROR_CANNOT_RESUME"
                    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "ERROR_DEVICE_NOT_FOUND"
                    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "ERROR_FILE_ALREADY_EXISTS"
                    DownloadManager.ERROR_FILE_ERROR -> "ERROR_FILE_ERROR"
                    DownloadManager.ERROR_HTTP_DATA_ERROR -> "ERROR_HTTP_DATA_ERROR"
                    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "ERROR_INSUFFICIENT_SPACE"
                    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "ERROR_TOO_MANY_REDIRECTS"
                    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "ERROR_UNHANDLED_HTTP_CODE"
                    DownloadManager.ERROR_UNKNOWN -> "ERROR_UNKNOWN"
                    else -> ""
                }
            }
            DownloadManager.STATUS_PAUSED -> {
                statusText = "STATUS_PAUSED"
                reasonText = when (reason) {
                    DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "PAUSED_QUEUED_FOR_WIFI"
                    DownloadManager.PAUSED_UNKNOWN -> "PAUSED_UNKNOWN"
                    DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "PAUSED_WAITING_FOR_NETWORK"
                    DownloadManager.PAUSED_WAITING_TO_RETRY -> "PAUSED_WAITING_TO_RETRY"
                    else -> ""
                }
            }
            DownloadManager.STATUS_PENDING -> statusText = "STATUS_PENDING"
            DownloadManager.STATUS_RUNNING -> statusText = "STATUS_RUNNING"
            DownloadManager.STATUS_SUCCESSFUL -> statusText = "STATUS_SUCCESSFUL"
        }

        return "Status: $statusText, $reasonText"
    }

    companion object {
        private const val TRACKING_STATUS_DELAY = 500L
    }
}
