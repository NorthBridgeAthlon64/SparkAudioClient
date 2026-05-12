package com.sleaudioclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleaudioclient.model.PcmFormat
import com.sleaudioclient.model.UiState
import com.sleaudioclient.viewmodel.AudioClientViewModel

@Composable
fun AudioClientScreen(
    uiState: UiState,
    viewModel: AudioClientViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("SLE UDP Audio Client", style = MaterialTheme.typography.headlineSmall)
        Text("状态: ${uiState.status}")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.start() }, enabled = !uiState.isRunning) {
                Text("开始")
            }
            Button(onClick = { viewModel.stop() }, enabled = uiState.isRunning) {
                Text("停止")
            }
            Button(onClick = { viewModel.probe() }) {
                Text("再次探测")
            }
            Button(onClick = { viewModel.resetStats() }) {
                Text("清零统计")
            }
        }

        HorizontalDivider()
        Text("连接参数", style = MaterialTheme.typography.titleMedium)
        ConfigTextField("设备IP", uiState.config.serverIp, viewModel::setServerIp)
        ConfigTextField("设备端口", uiState.config.serverPort.toString(), viewModel::setServerPort)
        ConfigTextField("本地绑定IP", uiState.config.bindIp, viewModel::setBindIp)
        ConfigTextField("本地绑定端口(0=随机)", uiState.config.bindPort.toString(), viewModel::setBindPort)
        ConfigTextField(
            "心跳间隔(ms)",
            uiState.config.heartbeatMs.toString(),
            viewModel::setHeartbeatMs
        )
        Text(
            "默认1000，范围200~120000；须小于固件空闲超时；周期性向设备发 0x01 刷新 peer。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()
        Text("音频参数", style = MaterialTheme.typography.titleMedium)
        ConfigTextField("采样率", uiState.config.sampleRate.toString(), viewModel::setSampleRate)
        ConfigTextField("声道(1/2)", uiState.config.channels.toString(), viewModel::setChannels)
        ConfigTextField("预缓冲(ms)", uiState.config.prebufferMs.toString(), viewModel::setPrebufferMs)
        ConfigTextField("音量(0~2)", uiState.config.volume.toString(), viewModel::setVolume)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("播放开关")
            Switch(checked = uiState.config.playbackEnabled, onCheckedChange = viewModel::setPlaybackEnabled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.setPcmFormat(PcmFormat.INT16) },
                enabled = uiState.config.pcmFormat != PcmFormat.INT16
            ) {
                Text("INT16")
            }
            Button(
                onClick = { viewModel.setPcmFormat(PcmFormat.INT32) },
                enabled = uiState.config.pcmFormat != PcmFormat.INT32
            ) {
                Text("INT32")
            }
            Text("当前: ${uiState.config.pcmFormat}")
        }

        HorizontalDivider()
        Text("统计", style = MaterialTheme.typography.titleMedium)
        Text("包数: ${uiState.stats.packets}")
        Text("字节: ${uiState.stats.bytes}")
        Text("速率: ${uiState.stats.bytesPerSecond} B/s")
        Text("队列深度: ${uiState.stats.queueDepth}")
        Text("队列丢包: ${uiState.stats.queueDrops}")
        Text("播放下溢: ${uiState.stats.underruns}")

        HorizontalDivider()
        Text("HEX预览(最近包前48字节)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = uiState.lastPacketHex,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            readOnly = true,
            label = { Text("HEX") }
        )
    }
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true
    )
}
