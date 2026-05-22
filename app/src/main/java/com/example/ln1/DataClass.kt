package com.example.ln1

// 登录响应
data class LoginResponse(
    val userId: Int,
    val status: String
)

// 用户信息
data class User(
    val id: Int,
    val username: String,
    val email: String
)

data class LoginReq(
    val username: String,
    val password: String
)

data class RegisterReq(
    val username: String,
    val password: String,
    val email: String
)

data class SendMailReq(
    val userId: Int,      // 发件人ID
    val receiver: String, // 收件人用户名
    val subject: String,
    val content: String
)

data class Mail(
    val id: Int,
    val sender: String,
    val subject: String,
    val content: String,
    val isRead: Int,
    val createTime: String,
    val attachments: List<AttachmentInfo>? = null
)

data class AttachmentInfo(
    val id: Int,
    val fileName: String,
    val filePath: String,
    val fileSize: Long
)

data class AttachmentItem(
    val uri: android.net.Uri,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String
)

data class ApiResponse(
    val status: String
)

data class MailListResponse(
    val list: List<Mail>
)