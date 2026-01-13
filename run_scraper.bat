@echo off
chcp 65001 >nul
title Turkish Series Scraper
color 0A

:menu
cls
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║                  Turkish Series Scraper                       ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.
echo   [1] All Series - New Episodes Only
echo   [2] All Series - Full Scrape
echo.
echo   [3] Single Series - New Episodes Only
echo   [4] Single Series - Full Scrape
echo.
echo   [5] Push Changes to GitHub
echo   [6] Pull Latest from GitHub
echo.
echo   [0] Exit
echo.
echo ═══════════════════════════════════════════════════════════════
echo.
set /p choice=Choose option:

if "%choice%"=="1" goto new_all
if "%choice%"=="2" goto full_all
if "%choice%"=="3" goto new_single
if "%choice%"=="4" goto full_single
if "%choice%"=="5" goto push
if "%choice%"=="6" goto pull
if "%choice%"=="0" goto exit
goto menu

:new_all
cls
echo.
echo    Fetching new episodes for all series...
echo ═══════════════════════════════════════════════════════════════
echo.
cd /d "%~dp0scraper"
python main.py --all
echo.
echo ═══════════════════════════════════════════════════════════════
echo                        Done!
echo ═══════════════════════════════════════════════════════════════
pause
goto menu

:full_all
cls
echo.
echo    Full scrape for all series...
echo ═══════════════════════════════════════════════════════════════
echo.
cd /d "%~dp0scraper"
python main.py --all --full
echo.
echo ═══════════════════════════════════════════════════════════════
echo                        Done!
echo ═══════════════════════════════════════════════════════════════
pause
goto menu

:new_single
cls
echo.
echo    Fetch new episodes for single series
echo ═══════════════════════════════════════════════════════════════
echo.
set /p series_id=Enter series ID:
if "%series_id%"=="" goto menu
echo.
cd /d "%~dp0scraper"
python main.py --series %series_id%
echo.
echo ═══════════════════════════════════════════════════════════════
echo                        Done!
echo ═══════════════════════════════════════════════════════════════
pause
goto menu

:full_single
cls
echo.
echo    Full scrape for single series
echo ═══════════════════════════════════════════════════════════════
echo.
set /p series_id=Enter series ID:
if "%series_id%"=="" goto menu
echo.
cd /d "%~dp0scraper"
python main.py --series %series_id% --full
echo.
echo ═══════════════════════════════════════════════════════════════
echo                        Done!
echo ═══════════════════════════════════════════════════════════════
pause
goto menu

:push
cls
echo.
echo    Pushing changes to GitHub...
echo ═══════════════════════════════════════════════════════════════
echo.
cd /d "%~dp0"
git add data/
git commit -m "Manual update series data"
git push origin main
if %errorlevel% neq 0 (
    echo.
    echo    Error - trying pull first...
    git pull --rebase origin main
    git push origin main
)
echo.
echo ═══════════════════════════════════════════════════════════════
echo                        Push complete!
echo ═══════════════════════════════════════════════════════════════
pause
goto menu

:pull
cls
echo.
echo    Pulling latest changes...
echo ═══════════════════════════════════════════════════════════════
echo.
cd /d "%~dp0"
git pull origin main
echo.
echo ═══════════════════════════════════════════════════════════════
echo                        Done!
echo ═══════════════════════════════════════════════════════════════
pause
goto menu

:exit
exit
