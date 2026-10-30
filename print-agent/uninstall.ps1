# Print-agentni to'xtatadi va avtoishga tushirishdan olib tashlaydi.
param([switch]$KeepFiles)
$ErrorActionPreference = 'SilentlyContinue'

Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" |
    Where-Object { $_.CommandLine -like '*DavronPrintAgent*print-agent.ps1*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Remove-Item (Join-Path ([Environment]::GetFolderPath('Startup')) 'DavronPrintAgent.vbs') -Force
if (-not $KeepFiles) {
    Remove-Item (Join-Path $env:LOCALAPPDATA 'DavronPrintAgent') -Recurse -Force
    Write-Host "Print-agent olib tashlandi."
}
