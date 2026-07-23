package com.example.vibic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.vibic.network.ApiClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddContactFragment : BottomSheetDialogFragment() {

    private lateinit var btnClose: ImageView
    private lateinit var phoneInput: EditText
    private lateinit var btnAddByPhone: TextView
    private lateinit var btnScanQr: LinearLayout

    private val qrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val qrValue = result.data?.getStringExtra(QrScannerActivity.RESULT_QR_VALUE)
            if (!qrValue.isNullOrBlank()) handleQrResult(qrValue)
        }
    }

    override fun getTheme() = R.style.BottomSheetTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_add_contacts, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnClose      = view.findViewById(R.id.btnClose)
        phoneInput    = view.findViewById(R.id.phoneInput)
        btnAddByPhone = view.findViewById(R.id.btnAddByPhone)
        btnScanQr     = view.findViewById(R.id.btnScanQr)

        phoneInput.addTextChangedListener(object : TextWatcher {
            var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true
                val digits = s.toString().filter { it.isDigit() }.take(10)
                val formatted = when {
                    digits.length <= 3 -> digits
                    digits.length <= 6 -> "${digits.take(3)} ${digits.drop(3)}"
                    digits.length <= 8 -> "${digits.take(3)} ${digits.drop(3).take(3)}-${digits.drop(6)}"
                    else               -> "${digits.take(3)} ${digits.drop(3).take(3)}-${digits.drop(6).take(2)}-${digits.drop(8)}"
                }
                s?.replace(0, s.length, formatted)
                isFormatting = false
            }
        })

        btnClose.setOnClickListener { dismiss() }
        btnAddByPhone.setOnClickListener { handleAddByPhone() }
        btnScanQr.setOnClickListener {
            qrLauncher.launch(Intent(requireContext(), QrScannerActivity::class.java))
        }
    }

    // ── По номеру телефона ────────────────────────────────────────────────────

    private fun handleAddByPhone() {
        val digits = phoneInput.text.toString().filter { it.isDigit() }
        if (digits.length < 10) {
            Toast.makeText(requireContext(), "Введите полный номер телефона", Toast.LENGTH_SHORT).show()
            return
        }
        searchAndOpenChat(phone = "7$digits")
    }

    // ── По QR-коду ────────────────────────────────────────────────────────────

    private fun handleQrResult(qrValue: String) {
        val userId = if (qrValue.startsWith("vibic://user/"))
            qrValue.removePrefix("vibic://user/").trim() else null

        if (userId != null) {
            openChatByUserId(userId)
        } else {
            val digits = qrValue.filter { it.isDigit() }
            if (digits.length >= 10) {
                val phone = when {
                    digits.length == 11 && digits.startsWith("8") -> "7${digits.drop(1)}"
                    digits.length == 11 -> digits
                    else -> "7$digits"
                }
                searchAndOpenChat(phone)
            } else {
                Toast.makeText(requireContext(), "Неизвестный QR-код", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Поиск по телефону ─────────────────────────────────────────────────────

    private fun searchAndOpenChat(phone: String) {
        setLoading(true)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.apiService.searchUsers(phone)
                }
                if (!response.isSuccessful) {
                    Toast.makeText(requireContext(), "Ошибка: ${response.code()}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val users = response.body().orEmpty()
                if (users.isEmpty()) {
                    Toast.makeText(requireContext(), "Пользователь не зарегистрирован в Vibic", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                openChatWithUser(users.first())
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    // ── Открыть чат по userId ─────────────────────────────────────────────────

    private fun openChatByUserId(userId: String) {
        setLoading(true)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.createPrivateChat(
                        CreatePrivateChatRequest(target_user_id = userId)
                    )
                }
                if (resp.isSuccessful) {
                    val chat = resp.body() ?: return@launch
                    dismiss()
                    startActivity(Intent(requireContext(), ChatDetailActivity::class.java).apply {
                        putExtra("chat_id", chat.id)
                        putExtra("chat_name", chat.name ?: "Контакт")
                    })
                } else {
                    Toast.makeText(requireContext(), "Ошибка: ${resp.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun openChatWithUser(user: UserResponse) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.apiService.createPrivateChat(
                        CreatePrivateChatRequest(target_user_id = user.id)
                    )
                }
                if (resp.isSuccessful) {
                    val chat = resp.body() ?: return@launch
                    val name = listOfNotNull(
                        user.first_name?.trim()?.takeIf { it.isNotEmpty() },
                        user.last_name?.trim()?.takeIf { it.isNotEmpty() }
                    ).joinToString(" ").ifBlank { user.phone ?: "Контакт" }
                    dismiss()
                    startActivity(Intent(requireContext(), ChatDetailActivity::class.java).apply {
                        putExtra("chat_id", chat.id)
                        putExtra("chat_name", name)
                    })
                } else {
                    Toast.makeText(requireContext(), "Ошибка создания чата", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnAddByPhone.isEnabled = !loading
        btnAddByPhone.text = if (loading) "Поиск..." else "Добавить"
    }
}