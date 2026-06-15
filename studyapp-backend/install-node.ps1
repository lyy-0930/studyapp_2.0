# Node.js 安装脚本（无需管理员权限）
# 将Node.js安装到用户目录并添加到当前会话的PATH

Write-Host "=== 安装Node.js（用户级别）===" -ForegroundColor Cyan

# 1. 设置路径
$nodeVersion = "v20.15.0"
$nodeUrl = "https://nodejs.org/dist/$nodeVersion/node-$nodeVersion-win-x64.zip"
$tempZip = "$env:TEMP\nodejs.zip"
$installDir = "$env:USERPROFILE\nodejs"
$nodeExePath = "$installDir\node.exe"

Write-Host "下载Node.js $nodeVersion..." -ForegroundColor Yellow
# 下载Node.js
try {
    Invoke-WebRequest -Uri $nodeUrl -OutFile $tempZip -ErrorAction Stop
    Write-Host "下载完成" -ForegroundColor Green
} catch {
    Write-Host "下载失败: $_" -ForegroundColor Red
    exit 1
}

# 2. 解压文件
Write-Host "解压到 $installDir..." -ForegroundColor Yellow
if (Test-Path $installDir) {
    Remove-Item -Path $installDir -Recurse -Force -ErrorAction SilentlyContinue
}

# 创建目录
New-Item -ItemType Directory -Path $installDir -Force | Out-Null

# 解压zip文件
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($tempZip, $installDir)

    # Node.js解压后通常在node-$nodeVersion-win-x64目录中
    $extractedDir = Get-ChildItem -Path $installDir -Directory | Select-Object -First 1
    if ($extractedDir) {
        # 移动所有文件到安装目录
        Move-Item -Path "$($extractedDir.FullName)\*" -Destination $installDir -Force
        Remove-Item -Path $extractedDir.FullName -Recurse -Force
    }

    Write-Host "解压完成" -ForegroundColor Green
} catch {
    Write-Host "解压失败: $_" -ForegroundColor Red
    exit 1
}

# 3. 添加到当前会话的PATH
$nodeBinDir = $installDir
$npmPath = "$nodeBinDir\npm.cmd"

if (Test-Path $nodeExePath) {
    $env:Path = "$nodeBinDir;$env:Path"
    Write-Host "已将Node.js添加到当前会话的PATH" -ForegroundColor Green
} else {
    Write-Host "找不到node.exe，安装可能失败" -ForegroundColor Red
    exit 1
}

# 4. 验证安装
Write-Host "`n验证安装..." -ForegroundColor Cyan
$nodeVersionOutput = & "$nodeExePath" --version 2>&1
$npmVersionOutput = & "$npmPath" --version 2>&1

Write-Host "Node.js版本: $nodeVersionOutput" -ForegroundColor Green
Write-Host "npm版本: $npmVersionOutput" -ForegroundColor Green

Write-Host "`n✅ Node.js安装成功！" -ForegroundColor Green
Write-Host "安装目录: $installDir" -ForegroundColor Gray
Write-Host "注意：此PATH更改仅对当前PowerShell会话有效。" -ForegroundColor Yellow
Write-Host "要永久添加，请将 '$nodeBinDir' 添加到系统环境变量PATH中。" -ForegroundColor Yellow

# 5. 提示下一步
Write-Host "`n下一步：" -ForegroundColor Cyan
Write-Host "1. 保持此PowerShell窗口打开" -ForegroundColor White
Write-Host "2. 在此窗口中运行: cd '$PSScriptRoot'" -ForegroundColor White
Write-Host "3. 然后运行: npm install" -ForegroundColor White
Write-Host "4. 最后运行: npm start" -ForegroundColor White