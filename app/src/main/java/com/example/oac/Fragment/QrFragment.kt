package com.example.oac.Fragment

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.oac.R
import com.google.zxing.integration.android.IntentIntegrator

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import java.io.IOException
import org.json.JSONObject


class QrFragment : Fragment() {

    private lateinit var deviceId: String
    private lateinit var oacNumberEditText: EditText
    private lateinit var phoneNumberEditText: EditText
    private lateinit var mailAddressEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var scanButton: Button
    private lateinit var resultTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.qr_fragment, container, false)

        // 初期化
        deviceId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        oacNumberEditText = view.findViewById(R.id.editTextOacNumber)
        phoneNumberEditText = view.findViewById(R.id.editTextPhoneNumber)
        mailAddressEditText = view.findViewById(R.id.editTextMailAddress)
        sendButton = view.findViewById(R.id.buttonSend)
        scanButton = view.findViewById(R.id.buttonScan)
        resultTextView = view.findViewById(R.id.textViewResult)

        scanButton.setOnClickListener {
            // QRコードスキャンを開始
            startQRScanner()
        }

        sendButton.setOnClickListener {
            // データ送信処理
            postData()
        }

        return view
    }

    private fun startQRScanner() {
        val integrator = IntentIntegrator.forSupportFragment(this) // Fragment 内で使用
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE) // QRコード形式を指定
        integrator.setPrompt("QRコードをスキャンしてください")
        integrator.setCameraId(0) // デフォルトのカメラ（背面カメラ）を使用
        integrator.setBeepEnabled(true) // スキャン成功時の音を有効化
        integrator.setBarcodeImageEnabled(false) // スキャン結果の画像を保存しない
        integrator.initiateScan()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(requireContext(), "QRコードがスキャンされませんでした", Toast.LENGTH_SHORT).show()
            } else {
                handleQRCodeResult(result.contents) // スキャン結果をハンドリング
            }
        }
    }

    private fun handleQRCodeResult(result: String) {
        if (result.length == 10 && result.all { it.isDigit() }) {
            oacNumberEditText.setText(result)
            resultTextView.text = "固有番号: $result"
        } else {
            resultTextView.text = "QRコードの内容が10桁の整数ではありません。"
        }
    }

    private fun postData() {
        val baseUrl = "http://arta.exp.mnb.ees.saitama-u.ac.jp/oac/common/update_passenger_contact.php"
        val oac = oacNumberEditText.text.toString()
        val phone = phoneNumberEditText.text.toString()
        val mail = mailAddressEditText.text.toString()

        if (oac.isEmpty() || phone.isEmpty() || mail.isEmpty()) {
            Toast.makeText(requireContext(), "すべての項目を入力してください", Toast.LENGTH_SHORT).show()
            return
        }

        val jsonBody = JSONObject().apply {
            put("oac", oac)
            put("spid", deviceId)
            put("tel", phone)
            put("mail", mail)
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "送信に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                requireActivity().runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "送信が完了しました", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "送信に失敗しました: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

}
