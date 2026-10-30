# Davron Elektro print-agent o'rnatuvchisi. Saytdan yuklangan DavronPrinter.cmd shu skriptni serverdan oladi
# (DAVRON_SERVER, DAVRON_TOKEN muhit o'zgaruvchilari bilan). Admin huquqi va printer drayveri kerak emas.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
trap {
    Write-Host "Xato: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Internetni tekshiring yoki saytdan faylni qayta yuklab oling."
    exit 1
}

$server = $env:DAVRON_SERVER.TrimEnd('/')
$headers = @{ 'X-Agent-Token' = $env:DAVRON_TOKEN }
$dir = Join-Path $env:LOCALAPPDATA 'DavronPrintAgent'
$script = Join-Path $dir 'print-agent.ps1'
$launcher = Join-Path ([Environment]::GetFolderPath('Startup')) 'DavronPrintAgent.vbs'

function Stop-Agent {
    Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" |
        Where-Object { $_.CommandLine -like '*DavronPrintAgent*print-agent.ps1*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

Write-Host "Davron Elektro: chek printeri agenti o'rnatilmoqda..."
Stop-Agent
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$bytes = (Invoke-WebRequest -Uri "$server/api/print-agent/script" -Headers $headers -UseBasicParsing -TimeoutSec 30).RawContentStream.ToArray()
[IO.File]::WriteAllBytes($script, $bytes)
@{ ServerUrl = $server; Token = $env:DAVRON_TOKEN } | ConvertTo-Json | Set-Content -Path (Join-Path $dir 'config.json') -Encoding ASCII

# Windows ga kirganda yashirin ishga tushadi (Unicode: foydalanuvchi papkasi nomi lotin bo'lmasa ham)
('CreateObject("WScript.Shell").Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{0}""", 0, False' -f $script) |
    Set-Content -Path $launcher -Encoding Unicode

@"
`$ErrorActionPreference = 'SilentlyContinue'
Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" |
    Where-Object { `$_.CommandLine -like '*DavronPrintAgent*print-agent.ps1*' } |
    ForEach-Object { Stop-Process -Id `$_.ProcessId -Force }
Remove-Item '$launcher' -Force
Remove-Item '$dir' -Recurse -Force
Write-Host "Chek printeri agenti o'chirildi."
"@ | Set-Content -Path (Join-Path $dir 'uninstall.ps1') -Encoding Unicode
"@powershell -NoProfile -ExecutionPolicy Bypass -File ""%~dp0uninstall.ps1"" & pause & exit /b" |
    Set-Content -Path (Join-Path $dir 'uninstall.cmd') -Encoding ASCII

Start-Process wscript.exe -ArgumentList "`"$launcher`""

# agent printerni topishini kutamiz va test chek chiqaramiz
Write-Host "Printer qidirilmoqda..."
$me = $null
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 1
    try {
        $me = Invoke-RestMethod -Uri "$server/api/print-agent/me" -Headers $headers -TimeoutSec 10
        if ($me.printerConnected) { break }
    } catch { }
}
Write-Host ""
if ($me -and $me.printerConnected) {
    Invoke-RestMethod -Method Post -Uri "$server/api/print-agent/test" -Headers $headers -TimeoutSec 10 | Out-Null
    Write-Host "Tayyor! Kassa: $($me.name). Printer: $($me.printerName)" -ForegroundColor Green
    Write-Host "Test chek chiqishi kerak. Endi har bir sotuvdan keyin chek o'zi chiqadi."
} elseif ($me -and $me.online) {
    Write-Host "Agent o'rnatildi (kassa: $($me.name)), lekin chek printeri topilmadi." -ForegroundColor Yellow
    Write-Host "Printerni USB ga ulang va yoqing - agent uni o'zi topadi, hech narsa o'rnatish shart emas."
} else {
    Write-Host "Agent o'rnatildi, lekin server bilan hali bog'lanmadi. Internetni tekshiring." -ForegroundColor Yellow
}
Write-Host "Log: $dir\agent.log   O'chirish: $dir\uninstall.cmd"
