package com.example.ln1

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    
    private var onAttachmentsSelected: ((List<AttachmentItem>) -> Unit)? = null
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val attachments = uris.mapNotNull { uri ->
            val fileName = getFileName(uri)
            val fileSize = getFileSize(uri)
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            AttachmentItem(uri, fileName, fileSize, mimeType)
        }
        onAttachmentsSelected?.invoke(attachments)
    }
    
    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
    
    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var userId by remember { mutableStateOf(0) }
                var username by remember { mutableStateOf("") }
                var showRegister by remember { mutableStateOf(false) }
                var showWriteMail by remember { mutableStateOf(false) }
                var selectedMail by remember { mutableStateOf<Mail?>(null) }
                var searchKeyword by remember { mutableStateOf("") }
                var mailList by remember { mutableStateOf(emptyList<Mail>()) }
                var isRefreshing by remember { mutableStateOf(false) }

                fun loadMails() {
                    isRefreshing = true
                    val currentKeyword = searchKeyword
                    val currentUsername = username
                    println("loadMails called, searchKeyword: '$currentKeyword', username: '$currentUsername'")
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val response = if (currentKeyword.isBlank()) {
                                RetrofitClient.api.getMails(currentUsername)
                            } else {
                                RetrofitClient.api.searchMail(currentUsername, currentKeyword)
                            }
                            if (response.isSuccessful) {
                                val newList = response.body()?.list ?: emptyList()
                                println("MainActivity: Loaded ${newList.size} mails")
                                withContext(Dispatchers.Main) {
                                    mailList = newList
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        withContext(Dispatchers.Main) {
                            isRefreshing = false
                        }
                    }
                }

                when {
                    showRegister -> {
                        RegisterScreen(onBack = { showRegister = false })
                    }
                    showWriteMail -> {
                        WriteMailScreen(
                            userId = userId,
                            senderName = username,
                            onSendSuccess = { 
                                showWriteMail = false 
                                loadMails()
                            },
                            onBack = { 
                                showWriteMail = false 
                            },
                            onPickAttachments = { callback ->
                                onAttachmentsSelected = callback
                                filePickerLauncher.launch(arrayOf(
                                    "image/*",
                                    "application/pdf",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/zip",
                                    "application/x-rar-compressed",
                                    "application/x-7z-compressed"
                                ))
                            }
                        )
                    }
                    selectedMail != null -> {
                        MailDetailScreen(
                            mail = selectedMail!!,
                            onBack = { 
                                selectedMail = null 
                                loadMails()
                            },
                            onDelete = { 
                                selectedMail = null 
                                loadMails()
                            }
                        )
                    }
                    userId == 0 -> {
                        LoginScreen(
                            onSuccess = { newUserId, newUsername ->
                                userId = newUserId
                                username = newUsername
                                loadMails()
                            },
                            onGoToRegister = { showRegister = true }
                        )
                    }
                    else -> {
                        InboxScreen(
                            username = username,
                            mailList = mailList,
                            isRefreshing = isRefreshing,
                            onRefresh = { loadMails() },
                            onGoToWriteMail = { showWriteMail = true },
                            onMailClick = { mail -> selectedMail = mail },
                            searchKeyword = searchKeyword,
                            onSearchChange = { searchKeyword = it },
                            onLogout = {
                                userId = 0
                                username = ""
                                mailList = emptyList()
                            }
                        )
                    }
                }
            }
        }
    }
}

// 登录页面
@Composable
fun LoginScreen(
    onSuccess: (Int, String) -> Unit,  // 修改：返回 userId 和 username
    onGoToRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("实训邮箱登录", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    tip = "账号或密码不能为空"
                } else {
                    isLoading = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("登录")
            }
        }

        TextButton(onClick = onGoToRegister) {
            Text("没有账号？去注册")
        }

        if (tip.isNotEmpty()) {
            Text(
                text = tip,
                color = if (tip.contains("成功")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            try {
                val response = RetrofitClient.api.login(username, password)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.status == "success" && body.userId > 0) {
                        tip = "✅ 登录成功"
                        // 登录成功后，同时传递 userId 和 username
                        onSuccess(body.userId, username)
                    } else {
                        tip = "❌ 登录失败：用户名或密码错误"
                    }
                } else {
                    tip = "❌ 服务器错误：${response.code()}"
                }
            } catch (e: Exception) {
                tip = "❌ 网络错误：${e.message}"
            }
            isLoading = false
        }
    }
}

// 收件箱页面（完美三点菜单 · 退出登录100%响应）
@Composable
fun InboxScreen(
    username: String,
    mailList: List<Mail>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onGoToWriteMail: () -> Unit,
    onMailClick: (Mail) -> Unit,
    searchKeyword: String,
    onSearchChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(searchKeyword) {
        onRefresh()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("收件箱", style = MaterialTheme.typography.headlineSmall)

                // 三个点按钮
                TextButton(onClick = {
                    showMenu = !showMenu
                }) {
                    Text("···", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            TextField(
                value = searchKeyword,
                onValueChange = onSearchChange,
                label = { Text("搜索邮件") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Button(onClick = onGoToWriteMail) {
                Text("写邮件")
            }
            Spacer(Modifier.height(16.dp))

            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (mailList.isEmpty()) {
                Text("暂无邮件", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(mailList, key = { it.id }) { mail ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = { onMailClick(mail) }
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("发件人：${mail.sender}")
                                    Text(if (mail.isRead == 1) "已读" else "未读")
                                }
                                Text("主题：${mail.subject}", style = MaterialTheme.typography.titleMedium)
                                Text("时间：${mail.createTime}", style = MaterialTheme.typography.bodySmall)
                                val attachments = mail.attachments ?: emptyList()
                                if (attachments.isNotEmpty()) {
                                    Text("附件：${attachments.size} 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 下拉菜单：退出登录
    if (showMenu) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 60.dp, end = 20.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Card(
                modifier = Modifier.width(160.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                // 退出登录按钮（100%可点击）
                Button(
                    onClick = {
                        showMenu = false
                        onLogout() // 一定能触发退出
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // 点击空白关闭菜单
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            TextButton(
                onClick = { showMenu = false },
                modifier = Modifier.fillMaxSize()
            ) {
                Text("")
            }
        }
    }
}

// 邮件详情页面
@Composable
fun MailDetailScreen(
    mail: Mail,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var isDeleting by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    var downloadTip by remember { mutableStateOf("") }

    // 标记为已读
    var markedAsRead by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        println("MailDetailScreen launched, mail.id=${mail.id}, mail.isRead=${mail.isRead}, markedAsRead=$markedAsRead")
        
        if (!markedAsRead) {
            markedAsRead = true
            println("Starting mark as read request for mailId: ${mail.id}")
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    println("Calling markAsRead API with mailId: ${mail.id.toString()}")
                    val response = RetrofitClient.api.markAsRead(mail.id.toString())
                    println("Mark as read response: isSuccessful=${response.isSuccessful}, body=${response.body()}")
                } catch (e: Exception) {
                    println("Mark as read error: ${e.message}")
                    e.printStackTrace()
                    markedAsRead = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("邮件详情", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("发件人：${mail.sender}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("主题：${mail.subject}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("时间：${mail.createTime}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Text("内容：", style = MaterialTheme.typography.labelMedium)
                Text(mail.content, style = MaterialTheme.typography.bodyLarge)
                
                val attachments = mail.attachments ?: emptyList()
                if (attachments.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("附件：", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(attachments) { attachment ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        downloadTip = "正在下载..."
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                val response = RetrofitClient.api.downloadFile(attachment.filePath)
                                                if (response.isSuccessful) {
                                                    val body = response.body()?.bytes()
                                                    if (body != null) {
                                                        val file = File(context.getExternalFilesDir(null), attachment.fileName)
                                                        FileOutputStream(file).use { it.write(body) }
                                                        withContext(Dispatchers.Main) {
                                                            downloadTip = "✅ 下载成功"
                                                        }
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        downloadTip = "❌ 下载失败"
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    downloadTip = "❌ 下载失败: ${e.message}"
                                                }
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "附件")
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium)
                                        Text("${attachment.fileSize} 字节", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Icon(Icons.Default.Download, contentDescription = "下载")
                                }
                            }
                        }
                    }
                    if (downloadTip.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(downloadTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onBack) {
                Text("返回")
            }
            Button(
                onClick = {
                    isDeleting = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("删除")
                }
            }
        }

        if (tip.isNotEmpty()) {
            Text(tip, color = MaterialTheme.colorScheme.error)
        }
    }

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            try {
                val response = RetrofitClient.api.deleteMail(mail.id.toString())
                if (response.isSuccessful) {
                    onDelete()
                } else {
                    tip = "删除失败"
                }
            } catch (e: Exception) {
                tip = "网络错误"
            }
            isDeleting = false
        }
    }
}

// 注册页面
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("用户注册", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        TextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    tip = "请填写完整信息"
                } else {
                    isLoading = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("注册")
            }
        }

        TextButton(onClick = onBack) {
            Text("返回登录")
        }

        if (tip.isNotEmpty()) {
            Text(
                tip,
                color = if (tip.contains("成功")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            try {
                val response = RetrofitClient.api.register(username, password)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.status == "ok") {
                        tip = "✅ 注册成功！"
                        delay(1000)
                        onBack()
                    } else {
                        tip = "❌ 注册失败"
                    }
                } else {
                    tip = "❌ 服务器错误"
                }
            } catch (e: Exception) {
                tip = "❌ 网络错误：${e.message}"
            }
            isLoading = false
        }
    }
}

// 写邮件页面
@Composable
fun WriteMailScreen(
    userId: Int,
    senderName: String,
    onSendSuccess: () -> Unit,
    onBack: () -> Unit,
    onPickAttachments: ((List<AttachmentItem>) -> Unit) -> Unit
) {
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<AttachmentItem>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text("写邮件", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        TextField(
            value = to,
            onValueChange = { to = it },
            label = { Text("收件人用户名") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        TextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("主题") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        TextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("正文") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onPickAttachments { newAttachments ->
                        attachments = attachments + newAttachments
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "添加附件")
                Spacer(Modifier.width(4.dp))
                Text("添加附件")
            }
            
            if (attachments.isNotEmpty()) {
                Text(
                    text = "(${attachments.size}个附件)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        
        if (attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("附件列表:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
            ) {
                items(attachments) { attachment ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = attachment.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatFileSize(attachment.fileSize),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachments = attachments.filter { it != attachment }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (to.isBlank() || subject.isBlank() || content.isBlank()) {
                    tip = "请填写完整信息"
                } else {
                    isSending = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSending
        ) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("发送邮件")
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("返回收件箱")
        }

        if (tip.isNotEmpty()) {
            Text(
                tip,
                color = if (tip.contains("成功")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }

    LaunchedEffect(isSending) {
        if (isSending) {
            try {
                val response = if (attachments.isEmpty()) {
                    RetrofitClient.api.sendMailSimple(senderName, to, subject, content)
                } else {
                    val parts = attachments.map { attachment ->
                        val tempFile = File(context.cacheDir, attachment.fileName)
                        context.contentResolver.openInputStream(attachment.uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        val mediaType = attachment.mimeType.toMediaTypeOrNull()
                        val requestFile = tempFile.asRequestBody(mediaType)
                        MultipartBody.Part.createFormData("attachments", attachment.fileName, requestFile)
                    }
                    
                    RetrofitClient.api.sendMail(
                        senderName.toRequestBody("text/plain".toMediaTypeOrNull()),
                        to.toRequestBody("text/plain".toMediaTypeOrNull()),
                        subject.toRequestBody("text/plain".toMediaTypeOrNull()),
                        content.toRequestBody("text/plain".toMediaTypeOrNull()),
                        parts
                    )
                }
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.status == "ok") {
                        tip = "✅ 发送成功"
                        delay(1000)
                        onSendSuccess()
                    } else {
                        tip = "❌ 发送失败"
                    }
                } else {
                    tip = "❌ 服务器错误"
                }
            } catch (e: Exception) {
                tip = "❌ 网络错误：${e.message}"
            }
            isSending = false
        }
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        else -> String.format("%.1f MB", size / (1024.0 * 1024))
    }
}