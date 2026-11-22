# AC4 mutation demo (ASCII only, safe for Windows PowerShell 5.1).
# Run from project root:
#   powershell -ExecutionPolicy Bypass -File scripts\mutation-demo.ps1
# Screenshot step 2 (tests FAIL) and step 3 (tests PASS again).
# NOTE: "mvn clean test" is used on purpose, otherwise Maven may keep the stale mutated .class files.

$root   = Split-Path -Parent $PSScriptRoot
$file   = Join-Path $root "src\main\java\com\example\library\service\BookService.java"
$backup = "$file.bak"
$utf8   = New-Object System.Text.UTF8Encoding($false)

Copy-Item $file $backup -Force
Push-Location $root
try {
    Write-Host ""
    Write-Host "=== STEP 1: BASELINE - tests must PASS ===" -ForegroundColor Cyan
    mvn clean test "-Dtest=BookServiceTest"

    Write-Host ""
    Write-Host "=== STEP 2: MUTATION - repository.delete() removed from BookService.delete() - test must FAIL ===" -ForegroundColor Yellow
    $src     = [System.IO.File]::ReadAllText($file)
    $mutated = $src.Replace("repository.delete(getEntity(id));", "getEntity(id); // MUTATION: delete removed")
    if ($mutated -eq $src) { throw "Mutation target line not found" }
    [System.IO.File]::WriteAllText($file, $mutated, $utf8)
    mvn clean test "-Dtest=BookServiceTest"
}
finally {
    Copy-Item $backup $file -Force
    (Get-Item $file).LastWriteTime = Get-Date
    Remove-Item $backup -Force
    Write-Host ""
    Write-Host "=== STEP 3: LOGIC RESTORED - tests must PASS again ===" -ForegroundColor Green
    mvn clean test "-Dtest=BookServiceTest"
    Pop-Location
}
