# Print-agentni joriy foydalanuvchiga o'rnatadi: Windows ga kirganda yashirin holda o'zi ishga tushadi.
# Ishlatish:  powershell -ExecutionPolicy Bypass -File install.ps1 -Token <PRINT_AGENT_TOKEN>
param(
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$ServerUrl = 'http://89.116.30.180:8082',
    [string]$PrinterName
)
$ErrorActionPreference = 'Stop'

if (-not $PrinterName) {
    $found = Get-Printer | Where-Object { $_.Name -match 'XP-58|Xprinter|POS-58' } | Select-Object -First 1
    if (-not $found) { throw "Xprinter topilmadi. Drayverni o'rnating yoki -PrinterName bering. Mavjud: $((Get-Printer).Name -join ', ')" }
    $PrinterName = $found.Name
}

$dir = Join-Path $env:LOCALAPPDATA 'DavronPrintAgent'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
& (Join-Path $PSScriptRoot 'uninstall.ps1') -KeepFiles
Copy-Item (Join-Path $PSScriptRoot 'print-agent.ps1') $dir -Force
@{ ServerUrl = $ServerUrl; Token = $Token; PrinterName = $PrinterName } | ConvertTo-Json |
    Set-Content -Path (Join-Path $dir 'config.json') -Encoding UTF8

$script = Join-Path $dir 'print-agent.ps1'
$launcher = Join-Path ([Environment]::GetFolderPath('Startup')) 'DavronPrintAgent.vbs'
@"
CreateObject("WScript.Shell").Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$script""", 0, False
"@ | Set-Content -Path $launcher -Encoding ASCII

Start-Process wscript.exe -ArgumentList "`"$launcher`""
Write-Host "O'rnatildi. Printer: $PrinterName, server: $ServerUrl"
Write-Host "Log: $(Join-Path $dir 'agent.log')"
