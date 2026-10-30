@echo off
rem Davron Elektro: chek printeri agentini shu kompyuterga o'rnatadi (kassa #{{ID}}).
rem Ikki marta bosing. Admin huquqi va printer drayveri kerak emas.
title Davron Elektro - chek printeri
set "DAVRON_SERVER={{SERVER}}"
set "DAVRON_TOKEN={{TOKEN}}"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$w = New-Object Net.WebClient; $w.Headers.Add('X-Agent-Token', $env:DAVRON_TOKEN); $w.Encoding = [Text.Encoding]::UTF8; iex $w.DownloadString($env:DAVRON_SERVER + '/api/print-agent/installer')"
echo.
pause
