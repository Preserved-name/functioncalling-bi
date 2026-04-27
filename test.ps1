# 多 Agent 系统测试脚本

# 1. 健康检查
Write-Host "=== 健康检查 ===" -ForegroundColor Green
Invoke-RestMethod -Uri "http://localhost:8080/api/health" -Method Get

Write-Host "`n`n=== 测试完成 ===" -ForegroundColor Green
