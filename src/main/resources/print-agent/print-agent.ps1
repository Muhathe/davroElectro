# Davron Elektro print-agent (kassa kompyuterida yashirin ishlaydi).
# Serverdagi chek navbatini so'raydi (long-poll) va ESC/POS baytlarini printerga yuboradi:
#  1) to'g'ridan-to'g'ri USB ga - Windows ning o'z usbprint drayveri orqali, Xprinter drayveri shart emas;
#  2) USB chek printeri topilmasa - Windows dagi chek printeri navbatiga (RAW).
# Sozlamalar: shu papkadagi config.json (ServerUrl, Token, ixtiyoriy PrinterName - faqat shu Windows printeri).
# O'rnatish: saytdan yuklangan DavronPrinter.cmd. Yangi versiyani serverdan o'zi oladi.
param([string]$ConfigPath = (Join-Path $PSScriptRoot 'config.json'))

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ikkita agent bir chekni ikki marta chiqarmasin; yangilangan agent eskisi chiqib ketishini kutadi
$mutex = New-Object System.Threading.Mutex($false, 'Local\DavronElektroPrintAgent')
try { $owned = $mutex.WaitOne(20000) } catch [System.Threading.AbandonedMutexException] { $owned = $true }
if (-not $owned) { exit 0 }

$cfg = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$base = $cfg.ServerUrl.TrimEnd('/')
$self = $PSCommandPath
$version = (Get-FileHash $self -Algorithm SHA256).Hash.ToLower()
$logFile = Join-Path $PSScriptRoot 'agent.log'

function Write-Log([string]$msg) {
    if ((Test-Path $logFile) -and (Get-Item $logFile).Length -gt 1MB) {
        Move-Item $logFile "$logFile.old" -Force
    }
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg" | Add-Content -Path $logFile -Encoding UTF8
}

Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using Microsoft.Win32.SafeHandles;

// USB printer klassidagi qurilmalar (GUID_DEVINTERFACE_USBPRINT) - Windows ularni o'zi usbprint.sys ga bog'laydi
public static class DavronUsb {
    static Guid UsbPrint = new Guid("28d78fad-5a12-11d1-ae5b-0000f803a8c2");
    const int DIGCF_PRESENT = 0x2, DIGCF_DEVICEINTERFACE = 0x10;
    const uint GENERIC_READ = 0x80000000, GENERIC_WRITE = 0x40000000, OPEN_EXISTING = 3, FILE_FLAG_OVERLAPPED = 0x40000000;
    const uint IOCTL_USBPRINT_GET_1284_ID = 0x220034;

    [StructLayout(LayoutKind.Sequential)]
    struct SP_DEVICE_INTERFACE_DATA { public int cbSize; public Guid InterfaceClassGuid; public int Flags; public IntPtr Reserved; }

    [DllImport("setupapi.dll", SetLastError = true)]
    static extern IntPtr SetupDiGetClassDevs(ref Guid g, IntPtr enumerator, IntPtr parent, int flags);
    [DllImport("setupapi.dll", SetLastError = true)]
    static extern bool SetupDiEnumDeviceInterfaces(IntPtr set, IntPtr devInfo, ref Guid g, int index, ref SP_DEVICE_INTERFACE_DATA data);
    [DllImport("setupapi.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern bool SetupDiGetDeviceInterfaceDetail(IntPtr set, ref SP_DEVICE_INTERFACE_DATA data, IntPtr detail, int size, out int required, IntPtr devInfo);
    [DllImport("setupapi.dll")]
    static extern bool SetupDiDestroyDeviceInfoList(IntPtr set);
    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern SafeFileHandle CreateFile(string name, uint access, uint share, IntPtr sa, uint disposition, uint flags, IntPtr template);
    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool DeviceIoControl(SafeFileHandle h, uint code, IntPtr inBuf, int inSize, byte[] outBuf, int outSize, out int returned, IntPtr overlapped);
    [DllImport("kernel32.dll")]
    static extern bool CancelIoEx(SafeFileHandle h, IntPtr overlapped);

    public static string[] DevicePaths() {
        List<string> list = new List<string>();
        IntPtr set = SetupDiGetClassDevs(ref UsbPrint, IntPtr.Zero, IntPtr.Zero, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
        if (set == new IntPtr(-1)) return list.ToArray();
        try {
            for (int i = 0; ; i++) {
                SP_DEVICE_INTERFACE_DATA d = new SP_DEVICE_INTERFACE_DATA();
                d.cbSize = Marshal.SizeOf(d);
                if (!SetupDiEnumDeviceInterfaces(set, IntPtr.Zero, ref UsbPrint, i, ref d)) break;
                int size;
                SetupDiGetDeviceInterfaceDetail(set, ref d, IntPtr.Zero, 0, out size, IntPtr.Zero);
                if (size <= 0) continue;
                IntPtr buf = Marshal.AllocHGlobal(size);
                try {
                    Marshal.WriteInt32(buf, IntPtr.Size == 8 ? 8 : 6);
                    if (SetupDiGetDeviceInterfaceDetail(set, ref d, buf, size, out size, IntPtr.Zero))
                        list.Add(Marshal.PtrToStringUni(new IntPtr(buf.ToInt64() + 4)));
                } finally { Marshal.FreeHGlobal(buf); }
            }
        } finally { SetupDiDestroyDeviceInfoList(set); }
        return list.ToArray();
    }

    static SafeFileHandle Open(string path, bool overlapped) {
        SafeFileHandle h = CreateFile(path, GENERIC_READ | GENERIC_WRITE, 3, IntPtr.Zero, OPEN_EXISTING,
            overlapped ? FILE_FLAG_OVERLAPPED : 0, IntPtr.Zero);
        if (h.IsInvalid) throw new Win32Exception(Marshal.GetLastWin32Error(), "USB printerni ochib bo'lmadi");
        return h;
    }

    /** IEEE-1284 ID (MFG:..;MDL:..;CMD:..). Ko'p arzon chek printerlari bo'sh qaytaradi. */
    public static string DeviceId(string path) {
        try {
            using (SafeFileHandle h = Open(path, false)) {
                byte[] buf = new byte[1024];
                int n;
                if (!DeviceIoControl(h, IOCTL_USBPRINT_GET_1284_ID, IntPtr.Zero, 0, buf, buf.Length, out n, IntPtr.Zero) || n <= 2) return "";
                int len = Math.Min(n, (buf[0] << 8) | buf[1]);
                return len <= 2 ? "" : Encoding.ASCII.GetString(buf, 2, len - 2).Trim('\0', ' ');
            }
        } catch (Exception) {
            return "";
        }
    }

    public static void Write(string path, byte[] data, int timeoutMs) {
        using (SafeFileHandle h = Open(path, true))
        using (FileStream fs = new FileStream(h, FileAccess.ReadWrite, 1, true)) {
            Task t = fs.WriteAsync(data, 0, data.Length);
            bool finished;
            try {
                finished = t.Wait(timeoutMs);
            } catch (AggregateException e) {
                throw new IOException("Printerga yozib bo'lmadi: " + e.InnerException.Message);
            }
            if (!finished) {
                CancelIoEx(h, IntPtr.Zero);
                throw new TimeoutException("Printer javob bermayapti (qog'oz, qopqoq yoki kabelni tekshiring)");
            }
        }
    }
}

// Windows printer navbati orqali RAW (drayveri o'rnatilgan printer uchun zaxira yo'l)
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

$idCache = @{}

# Chek printerimi? 1284 ID da POS belgilari bo'lsa - ha; to'liq ID li boshqa printer (ofis lazer printeri) - yo'q;
# ID bo'sh bo'lsa (Xprinter va ko'p arzon POS printerlar shunday) - ha.
function Test-ReceiptPrinter([string]$id) {
    if ($id -match 'ESC/?POS|\bPOS\b|THERMAL|RECEIPT|XPRINTER|\bXP-\d|\bTM-') { return $true }
    if ($id -match 'MFG:|MANUFACTURER:') { return $false }
    return $true
}

function Find-ReceiptPrinter {
    if ($cfg.PrinterName) {
        return @{ Kind = 'queue'; Target = $cfg.PrinterName; Name = $cfg.PrinterName }
    }
    foreach ($path in [DavronUsb]::DevicePaths()) {
        if (-not $idCache.ContainsKey($path)) { $idCache[$path] = [DavronUsb]::DeviceId($path) }
        $id = $idCache[$path]
        if (-not (Test-ReceiptPrinter $id)) { continue }
        $name = 'USB chek printeri'
        if ($id -match 'MDL:([^;]+)') { $name = "USB $($Matches[1].Trim())" }
        if ($path -match 'vid_([0-9a-f]{4})&pid_([0-9a-f]{4})') { $name += " (VID_$($Matches[1].ToUpper()) PID_$($Matches[2].ToUpper()))" }
        return @{ Kind = 'usb'; Target = $path; Name = $name }
    }
    # USB da topilmadi: drayveri o'rnatilgan chek printeri bo'lsa, Windows navbati orqali
    $queue = Get-CimInstance Win32_Printer -Filter 'WorkOffline = FALSE' |
        Where-Object { $_.Name -match 'XP-\d|Xprinter|POS-?\d|Receipt|Thermal' } | Select-Object -First 1
    if ($queue) { return @{ Kind = 'queue'; Target = $queue.Name; Name = $queue.Name } }
    return $null
}

function Send-ToPrinter($printer, [byte[]]$data, [string]$docName) {
    if ($printer.Kind -eq 'usb') {
        [DavronUsb]::Write($printer.Target, $data, 15000)
    } else {
        [RawPrinter]::Send($printer.Target, $data, $docName)
    }
}

function Get-StatusCode($err) {
    $resp = $err.Exception.Response
    if ($resp) { return [int]$resp.StatusCode }
    return 0
}

# Serverda boshqa versiya bo'lsa yuklab oladi va o'zini qayta ishga tushiradi
function Update-Self {
    $h = @{ 'X-Agent-Token' = $cfg.Token }
    $latest = (Invoke-WebRequest -Uri "$base/api/print-agent/version" -Headers $h -UseBasicParsing -TimeoutSec 15).Content.Trim()
    if (-not $latest -or $latest -eq $version) { return }
    $bytes = (Invoke-WebRequest -Uri "$base/api/print-agent/script" -Headers $h -UseBasicParsing -TimeoutSec 30).RawContentStream.ToArray()
    $sha = [Security.Cryptography.SHA256]::Create()
    $hash = ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '').ToLower()
    if ($hash -ne $latest) { Write-Log "Yangilanish to'liq yuklanmadi, keyinroq"; return }
    [IO.File]::WriteAllBytes($self, $bytes)
    Write-Log "Agent yangilandi ($($version.Substring(0, 8)) -> $($latest.Substring(0, 8))), qayta ishga tushyapti"
    Start-Process powershell.exe -WindowStyle Hidden -ArgumentList "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$self`""
    $mutex.ReleaseMutex()
    exit 0
}

function Send-Ack([long]$id, [string]$result, [string]$errorText, $headers) {
    $uri = "$base/api/print-agent/jobs/$id/$result"
    if ($errorText) {
        $body = [Text.Encoding]::UTF8.GetBytes((@{ error = $errorText } | ConvertTo-Json -Compress))
        Invoke-WebRequest -Method Post -Uri $uri -Headers $headers -ContentType 'application/json; charset=utf-8' `
            -Body $body -UseBasicParsing -TimeoutSec 15 | Out-Null
    } else {
        Invoke-WebRequest -Method Post -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 15 | Out-Null
    }
}

Write-Log "Agent ishga tushdi: $base, versiya $($version.Substring(0, 8))"
$nextUpdateCheck = Get-Date
$lastState = $null
while ($true) {
    try {
        if ((Get-Date) -ge $nextUpdateCheck) {
            $nextUpdateCheck = (Get-Date).AddHours(1)
            try { Update-Self } catch { Write-Log "Yangilanishni tekshirib bo'lmadi: $($_.Exception.Message)" }
        }
        $printer = Find-ReceiptPrinter
        $state = if ($printer) { $printer.Name } else { '' }
        if ($state -ne $lastState) {
            Write-Log $(if ($printer) { "Printer: $($printer.Name)" } else { "Chek printeri topilmadi, USB ulanishini kutyapman" })
            $lastState = $state
        }
        $headers = @{ 'X-Agent-Token' = $cfg.Token; 'X-Agent-Printer' = $state; 'X-Agent-Version' = $version;
                      'X-Agent-Host' = $env:COMPUTERNAME }
        if (-not $printer) {
            # printer ulanmaguncha faqat "tirikman" deymiz, vazifa olmaymiz
            Invoke-WebRequest -Uri "$base/api/print-agent/jobs/next?wait=0" -Headers $headers -UseBasicParsing -TimeoutSec 15 | Out-Null
            Start-Sleep -Seconds 3
            continue
        }
        $resp = Invoke-WebRequest -Uri "$base/api/print-agent/jobs/next?wait=20" -Headers $headers -UseBasicParsing -TimeoutSec 40
        if ($resp.StatusCode -ne 200) { continue }
        $job = $resp.Content | ConvertFrom-Json
        try {
            Send-ToPrinter $printer ([Convert]::FromBase64String($job.data)) "Davron chek #$($job.id)"
            Send-Ack $job.id 'done' $null $headers
            Write-Log "Chop etildi: #$($job.id) $($job.type)"
        } catch {
            $err = $_.Exception.Message
            Write-Log "Printer xatosi #$($job.id): $err"
            $idCache.Clear()
            try { Send-Ack $job.id 'failed' $err $headers } catch { }
            Start-Sleep -Seconds 5
        }
    } catch {
        if ((Get-StatusCode $_) -eq 401) {
            Write-Log "Server bu kompyuterni tanimadi (kassa o'chirilgan yoki token eskirgan). Saytdan qayta o'rnating."
            Start-Sleep -Seconds 60
        } else {
            Write-Log "Server bilan aloqa yo'q: $($_.Exception.Message)"
            Start-Sleep -Seconds 10
        }
    }
}
