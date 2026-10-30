# Davron Elektro print-agent.
# Serverdagi chek navbatini so'raydi (long-poll) va chekni Windows printeriga RAW (ESC/POS) holida yuboradi.
# Sozlamalar: shu papkadagi config.json (ServerUrl, Token, PrinterName). O'rnatish: install.ps1.
param([string]$ConfigPath = (Join-Path $PSScriptRoot 'config.json'))

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ikkita agent bir chekni ikki marta chiqarmasin
$mutex = New-Object System.Threading.Mutex($false, 'Local\DavronElektroPrintAgent')
if (-not $mutex.WaitOne(0)) { exit 0 }

$cfg = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$base = $cfg.ServerUrl.TrimEnd('/')
$headers = @{ 'X-Agent-Token' = $cfg.Token; 'X-Agent-Printer' = $cfg.PrinterName }
$logFile = Join-Path $PSScriptRoot 'agent.log'

function Write-Log([string]$msg) {
    if ((Test-Path $logFile) -and (Get-Item $logFile).Length -gt 1MB) {
        Move-Item $logFile "$logFile.old" -Force
    }
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg" | Add-Content -Path $logFile -Encoding UTF8
}

Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class RawPrinter {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public class DOCINFO {
        [MarshalAs(UnmanagedType.LPWStr)] public string pDocName;
        [MarshalAs(UnmanagedType.LPWStr)] public string pOutputFile;
        [MarshalAs(UnmanagedType.LPWStr)] public string pDatatype;
    }

    [DllImport("winspool.drv", EntryPoint = "OpenPrinterW", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern bool OpenPrinter(string name, out IntPtr handle, IntPtr defaults);
    [DllImport("winspool.drv", SetLastError = true)]
    static extern bool ClosePrinter(IntPtr handle);
    [DllImport("winspool.drv", EntryPoint = "StartDocPrinterW", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern int StartDocPrinter(IntPtr handle, int level, [In, MarshalAs(UnmanagedType.LPStruct)] DOCINFO doc);
    [DllImport("winspool.drv", SetLastError = true)]
    static extern bool EndDocPrinter(IntPtr handle);
    [DllImport("winspool.drv", SetLastError = true)]
    static extern bool StartPagePrinter(IntPtr handle);
    [DllImport("winspool.drv", SetLastError = true)]
    static extern bool EndPagePrinter(IntPtr handle);
    [DllImport("winspool.drv", SetLastError = true)]
    static extern bool WritePrinter(IntPtr handle, byte[] data, int count, out int written);

    public static void Send(string printer, byte[] data, string docName) {
        IntPtr h;
        if (!OpenPrinter(printer, out h, IntPtr.Zero))
            throw new Win32Exception(Marshal.GetLastWin32Error(), "Printer topilmadi: " + printer);
        try {
            DOCINFO doc = new DOCINFO();
            doc.pDocName = docName;
            doc.pDatatype = "RAW";
            if (StartDocPrinter(h, 1, doc) == 0)
                throw new Win32Exception(Marshal.GetLastWin32Error());
            try {
                StartPagePrinter(h);
                int written;
                if (!WritePrinter(h, data, data.Length, out written) || written != data.Length)
                    throw new Win32Exception(Marshal.GetLastWin32Error(), "Printerga yozib bo'lmadi");
                EndPagePrinter(h);
            } finally {
                EndDocPrinter(h);
            }
        } finally {
            ClosePrinter(h);
        }
    }
}
'@

function Send-Ack([long]$id, [string]$result, [string]$errorText) {
    $uri = "$base/api/print-agent/jobs/$id/$result"
    if ($errorText) {
        $body = [Text.Encoding]::UTF8.GetBytes((@{ error = $errorText } | ConvertTo-Json -Compress))
        Invoke-WebRequest -Method Post -Uri $uri -Headers $headers -ContentType 'application/json; charset=utf-8' `
            -Body $body -UseBasicParsing -TimeoutSec 15 | Out-Null
    } else {
        Invoke-WebRequest -Method Post -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 15 | Out-Null
    }
}

Write-Log "Agent ishga tushdi: $base -> $($cfg.PrinterName)"
while ($true) {
    try {
        $resp = Invoke-WebRequest -Uri "$base/api/print-agent/jobs/next?wait=20" -Headers $headers -UseBasicParsing -TimeoutSec 40
        if ($resp.StatusCode -ne 200) { continue }
        $job = $resp.Content | ConvertFrom-Json
        try {
            [RawPrinter]::Send($cfg.PrinterName, [Convert]::FromBase64String($job.data), "Davron chek #$($job.id)")
            Send-Ack $job.id 'done' $null
            Write-Log "Chop etildi: #$($job.id) $($job.type)"
        } catch {
            $err = $_.Exception.Message
            Write-Log "Printer xatosi #$($job.id): $err"
            try { Send-Ack $job.id 'failed' $err } catch { }
            Start-Sleep -Seconds 5
        }
    } catch {
        Write-Log "Server bilan aloqa yo'q: $($_.Exception.Message)"
        Start-Sleep -Seconds 10
    }
}
