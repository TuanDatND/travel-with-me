$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pgCtl = Join-Path $projectRoot '.local/pgsql/bin/pg_ctl.exe'
$dataDirectory = Join-Path $projectRoot '.local/pgdata'
$logFile = Join-Path $projectRoot '.local/postgresql.log'
if (-not (Test-Path -LiteralPath $pgCtl) -or -not (Test-Path -LiteralPath $dataDirectory)) {
    throw 'Local PostgreSQL is not set up. See README.md.'
}
& $pgCtl -D $dataDirectory status
if ($LASTEXITCODE -ne 0) {
    & $pgCtl -D $dataDirectory -l $logFile -w start
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start local PostgreSQL. Check .local/postgresql.log.' }
}
