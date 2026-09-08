@echo off
setlocal
cd /d "%~dp0"
title antAI Cloudflare Tunnel

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start_tunnel.ps1"

pause
