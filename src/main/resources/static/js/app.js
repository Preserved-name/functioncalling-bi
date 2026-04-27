// 消息历史
let messageHistory = [];
// 打字机效果定时器
let typingTimers = {};

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

// 流式聊天
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
                sessionId: getSessionId() // 添加会话 ID
            })
        })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let accumulatedResponse = '';
            let intentInfo = null;
            
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
                    for (const line of lines) {
                        if (line.startsWith('event:')) {
                            const eventName = line.slice(6).trim();
                            continue;
                        }
                        
                        if (line.startsWith('data:')) {
                            const dataStr = line.slice(5).trim();
                            try {
                                const data = JSON.parse(dataStr);
                                
                                if (data.type === 'meta') {
                                    // 接收元信息
                                    intentInfo = {
                                        intent: data.intent,
                                        intentDescription: data.intentDescription,
                                        agentName: data.agentName
                                    };
                                } else if (data.type === 'chunk') {
                                    // 接收流式内容（使用打字机效果）
                                    const newContent = data.accumulated;
                                    typeWriterEffect(botMessageId, newContent);
                                    scrollToBottom();
                                } else if (data.type === 'complete') {
                                    // 完成 - 后端已清理 JSON
                                    const finalContent = data.response;
                                    // 确保最终内容完整显示
                                    typeWriterEffect(botMessageId, finalContent, true);
                                } else if (data.type === 'chart_event' || data.chartType) {
                                    // 收到图表数据，触发弹窗
                                    showChartModal(data);
                                }
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
        .then(() => {
            // 流式读取结束后，检查是否有图表事件（这里通过监听器处理更优雅，但为了简单我们在 fetch 后处理）
            // 实际上 SSE 的 event 监听需要在 EventSource 中做，由于我们用的是 fetch + reader，
            // 我们需要在上面的循环里直接触发弹窗。
        })
        .catch(error => {
            removeTypingIndicator(loadingId);
            reject(error);
        });
    });
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

// 更新 Bot 消息内容（直接更新，无动画）
function updateBotMessage(messageId, text) {
    const messageWrapper = document.getElementById(messageId);
    if (messageWrapper) {
        const contentDiv = messageWrapper.querySelector('.message-content');
        contentDiv.innerHTML = escapeHtml(text);
    }
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
    
    // 获取当前已显示的内容
    const currentText = contentDiv.textContent || '';
    
    // 如果新文本比当前文本长，逐字显示新增部分
    if (newText.length > currentText.length) {
        const displayNextChar = () => {
            const currentLength = contentDiv.textContent.length;
            if (currentLength < newText.length) {
                // 每次显示1-3个字符，让速度更快更自然
                const charsToShow = Math.min(3, newText.length - currentLength);
                const partialText = newText.substring(0, currentLength + charsToShow);
                contentDiv.innerHTML = escapeHtml(partialText);
                
                // 随机延迟，模拟真实打字速度
                const delay = Math.random() * 20 + 10; // 10-30ms
                typingTimers[messageId] = setTimeout(displayNextChar, delay);
            }
        };
        
        displayNextChar();
    } else {
        contentDiv.innerHTML = escapeHtml(newText);
    }
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
