package jp.su.mnb.oac.Fragment

import androidx.fragment.app.Fragment
import jp.su.mnb.oac.R

import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import java.util.*

class TrackingFragment : Fragment(R.layout.tracking_fragment) {

    private lateinit var webView: WebView

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        webView = view.findViewById(R.id.webView)
        webView.webViewClient = WebViewClient() // WebページをWebView内で表示する
        webView.settings.javaScriptEnabled = true // JavaScriptを有効にする

        loadWebPage()
    }

    private fun loadWebPage() {
        val deviceId = getDeviceId()
        val currentDate = getCurrentDate()

        val url = "http://arta.exp.mnb.ees.saitama-u.ac.jp/oac/common/show_location.php" +
                "?dev=$deviceId&date=$currentDate"

        webView.loadUrl(url)
    }

    // デバイス ID を取得
    private fun getDeviceId(): String {
        return Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
    }

    // 現在の日付を "yyyy-MM-dd" 形式で取得
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }
}
