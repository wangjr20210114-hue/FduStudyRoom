param([switch]$Buffer,[switch]$VerifyOnly)
$ErrorActionPreference='Stop'
$env:PYTHONUTF8='1'
$campusPython=Get-Command python -ErrorAction SilentlyContinue
if ($campusPython -and $campusPython.Source -like '*\WindowsApps\*') { $campusPython=$null }
if ($campusPython) {
    & $campusPython.Source -c 'import sys; sys.exit(0 if sys.version_info >= (3,12) else 1)' 2>$null
    if ($LASTEXITCODE -ne 0) { $campusPython=$null }
}
if (-not $campusPython) {
    $campusWinget=Get-Command winget -ErrorAction SilentlyContinue
    if (-not $campusWinget) { throw 'Python 3.12 or newer required. Install from https://www.python.org/downloads/ then retry.' }
    & winget install --exact --id Python.Python.3.13 --scope user --silent --disable-interactivity
    if ($LASTEXITCODE -ne 0) { throw 'Python installation requires manual OS setup. Stage stopped.' }
    $campusPythonPath=Join-Path $env:LOCALAPPDATA 'Programs\Python\Python313\python.exe'
} else { $campusPythonPath=$campusPython.Source }
$campusArguments=@((Join-Path $PSScriptRoot 'team.py'),'build','--install')
if (-not $VerifyOnly) { $campusArguments+='--start' }
if ($Buffer) { $campusArguments+='--buffer' }
& $campusPythonPath @campusArguments
if ($LASTEXITCODE -ne 0) { throw 'Build stopped; inspect the reported stage and .campus/build.local.' }
