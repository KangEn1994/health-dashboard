package com.healthdashboard.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.healthdashboard.mobile.SessionUiState

@Composable
fun LoginScreen(
    state: SessionUiState,
    onLogin: (String, String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var apiBaseUrl by rememberSaveable(state.apiBaseUrl) { mutableStateOf(state.apiBaseUrl) }

    Surface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            AppGradientHeader(
                title = "Health Dashboard",
                subtitle = "输入固定密码并配置接口地址，安卓端只保留记录管理与图表查看。",
                action = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                },
            )

            Spacer(modifier = Modifier.height(18.dp))

            SectionCard(
                title = "连接配置",
                subtitle = "支持模拟器、本地局域网服务器或公网域名。",
            ) {
                OutlinedTextField(
                    value = apiBaseUrl,
                    onValueChange = { apiBaseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API 接口地址") },
                    placeholder = { Text("https://health.kangen.fun:7894/") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("固定密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Button(
                    onClick = { onLogin(password, apiBaseUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    enabled = !state.isLoading && password.isNotBlank() && apiBaseUrl.isNotBlank(),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("连接并登录")
                    }
                }
            }
        }
    }
}
