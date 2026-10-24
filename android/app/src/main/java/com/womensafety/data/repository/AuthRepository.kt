package com.womensafety.data.repository

import android.content.Context
import com.womensafety.data.api.ApiClient
import com.womensafety.data.api.ApiService
import com.womensafety.data.models.*
import com.womensafety.utils.Resource
import com.womensafety.utils.SharedPrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository(private val context: Context) {

    private val api: ApiService = ApiClient.getService(context)
    private val prefs: SharedPrefManager = SharedPrefManager.getInstance(context)

    suspend fun login(request: LoginRequest): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Cache tokens and user information
                prefs.saveAuthTokens(body.tokens.access, body.tokens.refresh)
                prefs.saveUserData(
                    userId = body.user.id,
                    username = body.user.username,
                    email = body.user.email,
                    role = body.user.role
                )
                Resource.Success(body)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string())
                Resource.Error(errorMsg ?: "Login failed. Please check credentials.")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network connection error.")
        }
    }

    suspend fun register(
        request: RegisterRequest,
        idProofFile: File? = null
    ): Resource<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = if (idProofFile != null) {
                val textType = "text/plain".toMediaTypeOrNull()
                val parts = mutableMapOf<String, RequestBody>()
                parts["username"] = request.username.toRequestBody(textType)
                parts["email"] = request.email.toRequestBody(textType)
                request.phoneNumber?.let { parts["phone_number"] = it.toRequestBody(textType) }
                request.firstName?.let { parts["first_name"] = it.toRequestBody(textType) }
                request.lastName?.let { parts["last_name"] = it.toRequestBody(textType) }
                parts["role"] = request.role.toRequestBody(textType)
                parts["password"] = request.password.toRequestBody(textType)
                parts["confirm_password"] = request.confirmPassword.toRequestBody(textType)
                request.idProofType?.let { parts["id_proof_type"] = it.toRequestBody(textType) }

                val mimeType = when {
                    idProofFile.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    idProofFile.name.endsWith(".png", ignoreCase = true) -> "image/png"
                    else -> "image/jpeg"
                }
                val requestFile = idProofFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val bodyPart = MultipartBody.Part.createFormData("id_proof_document", idProofFile.name, requestFile)

                api.registerMultipart(parts, bodyPart)
            } else {
                api.register(request)
            }

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                prefs.saveAuthTokens(body.tokens.access, body.tokens.refresh)
                prefs.saveUserData(
                    userId = body.user.id,
                    username = body.user.username,
                    email = body.user.email,
                    role = body.user.role
                )
                Resource.Success(body)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string())
                Resource.Error(errorMsg ?: "Registration failed.")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network connection error.")
        }
    }

    suspend fun sendOtp(request: SendOtpRequest): Resource<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.sendOtp(request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string())
                Resource.Error(errorMsg ?: "Failed to send OTP.")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network connection error.")
        }
    }

    suspend fun verifyOtp(request: VerifyOtpRequest): Resource<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.verifyOtp(request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string())
                Resource.Error(errorMsg ?: "Failed to verify OTP.")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Network connection error.")
        }
    }

    suspend fun logout(): Resource<GenericResponse> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = prefs.getRefreshToken()
            if (!refreshToken.isNullOrEmpty()) {
                api.logout(mapOf("refresh" to refreshToken))
            }
            prefs.clear()
            Resource.Success(GenericResponse("Logged out successfully.", null, null))
        } catch (e: Exception) {
            prefs.clear()
            Resource.Error(e.localizedMessage ?: "Logout failed on server, cleared locally.")
        }
    }

    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrEmpty()) return null
        return try {
            val json = JSONObject(errorBody)
            when {
                json.has("detail") -> json.getString("detail")
                json.has("message") -> json.getString("message")
                json.has("email_or_phone") -> json.getString("email_or_phone")
                json.has("password") -> json.getString("password")
                else -> {
                    val keys = json.keys()
                    if (keys.hasNext()) {
                        val key = keys.next()
                        val value = json.opt(key)
                        if (value is org.json.JSONArray && value.length() > 0) {
                            value.getString(0)
                        } else {
                            value?.toString() ?: errorBody
                        }
                    } else {
                        errorBody
                    }
                }
            }
        } catch (e: Exception) {
            errorBody
        }
    }
}
