// 消息历史
let messageHistory = [];
// 打字机效果定时器
let typingTimers = {};
// 当前图表数据
let currentChartOption = null;

// 生成或获取会话 ID
function getSessionId() {
    let sessionId = localStorage.getItem('chat_session_id');
    if (!sessionId) {
        sessionId = 'session-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('chat_session_id', sessionId);
    }
    return sessionId;
}

// 发送消息函数（流式）
async function sendMessage() {
    const messageInput = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendBtn');
    
    const message = messageInput.value.trim();
    
    if (!message) {
        showNotification('请输入消息', 'warning');
        return;
    }
    
    // 添加用户消息到界面
    addUserMessage(message);
    
    // 清空输入框
    messageInput.value = '';
    adjustTextareaHeight();
    
    // 禁用发送按钮
    sendBtn.disabled = true;
    
    // 显示加载动画
    const loadingId = showTypingIndicator();
    
    try {
        // 使用 EventSource 进行流式请求
        await streamChat(message, loadingId);
    } catch (error) {
        console.error('Error:', error);
        removeTypingIndicator(loadingId);
        addErrorMessage(error.message);
    } finally {
        sendBtn.disabled = false;
    }
}

// 流式聊天 - 支持新协议
function streamChat(message, loadingId) {
    return new Promise((resolve, reject) => {
        // 创建 Bot 消息气泡
        const botMessageId = 'bot-' + Date.now();
        addBotMessage(botMessageId, '');
        
        // 使用 fetch 和 ReadableStream
        fetch('/api/chat/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ 
                message: message,
                sessionId: getSessionId()
            })
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let intentInfo = null;
            let accumulatedText = '';
            
            function readStream() {
                return reader.read().then(({ done, value }) => {
                    if (done) {
                        removeTypingIndicator(loadingId);
                        updateMessageInfo(botMessageId, intentInfo);
                        resolve();
                        return;
                    }
                    
                    // 解码数据
                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop(); // 保留不完整的行
                    
                    // 处理每一行
                    let eventName = null;
                    for (const line of lines) {
                        if (line.startsWith('event:')) {
                            eventName = line.slice(6).trim();
                            continue;
                        }
                        
                        if (line.startsWith('data:')) {
                            const dataStr = line.slice(5).trim();
                            try {
                                const data = JSON.parse(dataStr);
                                
                                // 处理不同类型的事件
                                if (eventName) {
                                    handleSseEvent(eventName, data, botMessageId, (info) => {
                                        intentInfo = info;
                                    }, (text) => {
                                        accumulatedText = text;
                                        typeWriterEffect(botMessageId, text);
                                        scrollToBottom();
                                    });
                                }
                                
                                // 重置 eventName
                                eventName = null;
                            } catch (e) {
                                console.error('Parse error:', e);
                            }
                        }
                    }
                    
                    return readStream();
                });
            }
            
            return readStream();
        })
        .catch(error => {
            removeTypingIndicator(loadingId);
            reject(error);
        });
    });
}

// 处理 SSE 事件
function handleSseEvent(eventName, data, botMessageId, setIntentInfo, updateText) {
    switch (eventName) {
        case 'meta':
            // 接收元信息
            setIntentInfo({
                intent: data.intent,
                intentDescription: data.intentDescription,
                agentName: data.agentName
            });
            break;
            
        case 'chunk':
            // 接收流式文本片段
            if (data.delta) {
                const currentText = getCurrentText(botMessageId);
                updateText(currentText + data.delta);
            }
            break;
            
        case 'chart':
            // 收到图表数据，触发弹窗
            console.log("收到图表数据:", data);
            showChartModal(data);
            break;
            
        case 'action':
            // 收到前端指令
            console.log("收到前端指令:", data);
            executeAction(data);
            break;
            
        case 'confirm':
            // 需要用户确认
            if (data.requiresConfirmation) {
                const confirmed = confirm(data.message || '确定执行此操作吗？');
                if (confirmed) {
                    // TODO: 发送确认信号给后端
                    showNotification('操作已确认', 'success');
                } else {
                    showNotification('操作已取消', 'info');
                }
            }
            break;
            
        case 'complete':
            // 完成信号
            console.log("流式传输完成:", data);
            break;
            
        default:
            console.warn('未知事件类型:', eventName);
    }
}

// 获取当前消息文本
function getCurrentText(messageId) {
    const messageWrapper = document.getElementById(messageId);
    if (!messageWrapper) return '';
    
    const contentDiv = messageWrapper.querySelector('.message-content');
    return contentDiv ? contentDiv.textContent : '';
}

// 执行前端指令
function executeAction(actionResult) {
    if (!actionResult || !actionResult.command) {
        console.error('无效的指令数据');
        return;
    }
    
    const command = actionResult.command.toLowerCase();
    const params = actionResult.params || {};
    
    switch (command) {
        case 'navigate':
            if (params.url) {
                // 构建完整 URL（包含查询参数）
                let url = params.url;
                if (params.query) {
                    const queryParams = new URLSearchParams(params.query).toString();
                    url += '?' + queryParams;
                }
                window.location.href = url;
            }
            break;
            
        case 'openmodal':
            if (params.modalId) {
                // 根据 payload 打开不同的弹窗
                openCustomModal(params);
            }
            break;
            
        case 'closemodal':
            closeAllModals();
            break;
            
        case 'refresh':
            location.reload();
            break;
            
        case 'custom':
            // 自定义指令，根据 actionType 处理
            handleCustomAction(params);
            break;
            
        default:
            console.warn('不支持的指令类型:', command);
    }
}

// 打开自定义弹窗
function openCustomModal(params) {
    const modalId = params.modalId;
    
    // 如果是图表弹窗，使用现有逻辑
    if (modalId === 'chart-modal' && params.payload) {
        showChartModal(params.payload);
    } else {
        // 其他弹窗类型（可扩展）
        showNotification(`打开弹窗: ${modalId}`, 'info');
    }
}

// 关闭所有弹窗
function closeAllModals() {
    closeChartModal();
    // 可以添加其他弹窗的关闭逻辑
}

// 处理自定义指令
function handleCustomAction(params) {
    const actionType = params.actionType;
    
    switch (actionType) {
        case 'export_data':
            // 导出数据
            if (params.endpoint) {
                exportData(params.endpoint, params.format, params.filters);
            }
            break;
        default:
            console.warn('未知的自定义指令类型:', actionType);
    }
}

// 导出数据
async function exportData(endpoint, format, filters) {
    try {
        showNotification('正在准备导出...', 'info');
        
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ format, filters })
        });
        
        if (response.ok) {
            // 下载文件
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `export_${Date.now()}.${format}`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
            
            showNotification('导出成功', 'success');
        } else {
            showNotification('导出失败', 'error');
        }
    } catch (error) {
        console.error('导出错误:', error);
        showNotification('导出失败: ' + error.message, 'error');
    }
}

// 添加用户消息
function addUserMessage(text) {
    const messagesContainer = document.getElementById('messagesContainer');
    const messageWrapper = document.createElement('div');
    messageWrapper.className = 'message-wrapper user-message';
    
    messageWrapper.innerHTML = `
        <div class="avatar user-avatar">👤</div>
        <div>
            <div class="message-bubble user-message">
                <p>${escapeHtml(text)}</p>
            </div>
            <div class="message-info">
                <span>${formatTime(new Date())}</span>
            </div>
        </div>
    `;
    
    messagesContainer.appendChild(messageWrapper);
    scrollToBottom();
    
    messageHistory.push({ role: 'user', content: text });
}

// 添加 Bot 消息
function addBotMessage(messageId, text) {
    const messagesContainer = document.getElementById('messagesContainer');
    const messageWrapper = document.createElement('div');
    messageWrapper.className = 'message-wrapper bot-message';
    messageWrapper.id = messageId;
    
    messageWrapper.innerHTML = `
        <div class="avatar bot-avatar">🤖</div>
        <div>
            <div class="message-bubble bot-message">
                <div class="message-content">${text || '<div class="typing-indicator"><div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div></div>'}</div>
            </div>
            <div class="message-info" style="display: none;">
                <span class="intent-info"></span>
                <span class="time-info"></span>
            </div>
        </div>
    `;
    
    messagesContainer.appendChild(messageWrapper);
    scrollToBottom();
}

// 打字机效果更新消息
function typeWriterEffect(messageId, newText, immediate = false) {
    const messageWrapper = document.getElementById(messageId);
    if (!messageWrapper) return;
    
    const contentDiv = messageWrapper.querySelector('.message-content');
    
    // 清除之前的定时器
    if (typingTimers[messageId]) {
        clearTimeout(typingTimers[messageId]);
        delete typingTimers[messageId];
    }
    
    if (immediate) {
        contentDiv.innerHTML = escapeHtml(newText);
        return;
    }
    
    // 直接更新内容（流式已经逐字发送）
    contentDiv.innerHTML = escapeHtml(newText);
}

// 更新消息信息
function updateMessageInfo(messageId, intentInfo) {
    const messageWrapper = document.getElementById(messageId);
    if (messageWrapper && intentInfo) {
        const infoDiv = messageWrapper.querySelector('.message-info');
        const intentSpan = infoDiv.querySelector('.intent-info');
        const timeSpan = infoDiv.querySelector('.time-info');
        
        const intentIcon = getIntentIcon(intentInfo.intent);
        intentSpan.textContent = `${intentIcon} ${intentInfo.agentName} · ${intentInfo.intentDescription}`;
        timeSpan.textContent = formatTime(new Date());
        infoDiv.style.display = 'flex';
    }
}

// 显示打字指示器
function showTypingIndicator() {
    const messagesContainer = document.getElementById('messagesContainer');
    const typingId = 'typing-' + Date.now();
    
    const typingWrapper = document.createElement('div');
    typingWrapper.className = 'message-wrapper bot-message';
    typingWrapper.id = typingId;
    
    typingWrapper.innerHTML = `
        <div class="avatar bot-avatar">🤖</div>
        <div class="message-bubble bot-message">
            <div class="typing-indicator">
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
                <div class="typing-dot"></div>
            </div>
        </div>
    `;
    
    messagesContainer.appendChild(typingWrapper);
    scrollToBottom();
    
    return typingId;
}

// 移除打字指示器
function removeTypingIndicator(typingId) {
    const typingElement = document.getElementById(typingId);
    if (typingElement) {
        typingElement.remove();
    }
}

// 添加错误消息
function addErrorMessage(errorMessage) {
    const messagesContainer = document.getElementById('messagesContainer');
    const messageWrapper = document.createElement('div');
    messageWrapper.className = 'message-wrapper bot-message';
    
    messageWrapper.innerHTML = `
        <div class="avatar bot-avatar">⚠️</div>
        <div class="message-bubble bot-message" style="border-color: #fc8181; background: #fff5f5;">
            <p style="color: #c53030;">❌ 发生错误：${escapeHtml(errorMessage)}</p>
        </div>
    `;
    
    messagesContainer.appendChild(messageWrapper);
    scrollToBottom();
}

// 使用示例问题
function useExample(text) {
    const messageInput = document.getElementById('messageInput');
    messageInput.value = text;
    messageInput.focus();
    sendMessage();
}

// 获取意图图标
function getIntentIcon(intent) {
    const icons = {
        'WEATHER': '🌤️',
        'MATH': '🔢',
        'KNOWLEDGE': '📚',
        'CODE': '💻',
        'BI_ANALYSIS': '📊',
        'GENERAL': '💬'
    };
    return icons[intent] || '❓';
}

// HTML 转义，防止 XSS 攻击
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML.replace(/\n/g, '<br>');
}

// 格式化时间
function formatTime(date) {
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return `${hours}:${minutes}`;
}

// 滚动到底部
function scrollToBottom() {
    const messagesContainer = document.getElementById('messagesContainer');
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// 自动调整文本框高度
function adjustTextareaHeight() {
    const textarea = document.getElementById('messageInput');
    textarea.style.height = 'auto';
    textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px';
}

// 显示通知
function showNotification(message, type = 'info') {
    // 可以使用更优雅的通知组件
    alert(message);
}

// 清除记忆
async function clearMemory() {
    if (!confirm('确定要清除所有对话记忆吗？')) {
        return;
    }
    
    try {
        // 清除本地存储的会话 ID
        localStorage.removeItem('chat_session_id');
        
        // 刷新页面以重新开始
        location.reload();
    } catch (error) {
        console.error('清除记忆失败:', error);
        alert('清除记忆失败');
    }
}

// 显示图表弹窗
function showChartModal(chartData) {
    console.log("收到图表数据:", chartData);
    const modal = document.getElementById('chartModal');
    const title = document.getElementById('chartTitle');
    const container = document.getElementById('chartContainer');
    
    title.textContent = chartData.title || '数据可视化';
    modal.style.display = 'flex';
    
    // 初始化 ECharts
    if (!container._echarts) {
        container._echarts = echarts.init(container);
    }
    const myChart = container._echarts;
    
    let option = {};
    let rawData = chartData.rawData;
    
    // 尝试解析 rawData (它可能是一个字符串，也可能已经是对象)
    if (typeof rawData === 'string') {
        try {
            // 如果字符串里包含 "查询成功..." 这样的前缀，尝试提取 JSON 数组部分
            const jsonMatch = rawData.match(/\[.*\]/s);
            if (jsonMatch) {
                rawData = JSON.parse(jsonMatch[0]);
            } else {
                rawData = JSON.parse(rawData);
            }
        } catch (e) {
            console.error("解析 rawData 失败", e);
            return;
        }
    }
    
    if (Array.isArray(rawData) && rawData.length > 0) {
        const keys = Object.keys(rawData[0]);
        if (keys.length >= 2) {
            const xAxisData = rawData.map(item => String(item[keys[0]]));
            const seriesData = rawData.map(item => Number(item[keys[1]]));
            
            option = {
                tooltip: { trigger: 'axis' },
                xAxis: { type: 'category', data: xAxisData, axisLabel: { rotate: 30 } },
                yAxis: { type: 'value' },
                series: [{
                    name: keys[1],
                    data: seriesData,
                    type: chartData.chartType === 'line' ? 'line' : (chartData.chartType === 'pie' ? 'pie' : 'bar'),
                    smooth: true
                }]
            };
            myChart.setOption(option, true);
        }
    }
}

// 关闭图表弹窗
function closeChartModal() {
    document.getElementById('chartModal').style.display = 'none';
}

// 初始化
document.addEventListener('DOMContentLoaded', function() {
    const messageInput = document.getElementById('messageInput');
    
    // 支持回车发送（Shift+Enter 换行）
    messageInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });
    
    // 自动调整高度
    messageInput.addEventListener('input', adjustTextareaHeight);
});
