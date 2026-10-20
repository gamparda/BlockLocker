param([Parameter(Mandatory=$true)][string]$ServerRoot)
$ErrorActionPreference='Stop'
$javac=(Get-Command javac.exe -ErrorAction Stop).Source
$jar=Join-Path (Split-Path $javac -Parent) 'jar.exe'
$root=(Resolve-Path -LiteralPath $ServerRoot).Path
$libraries=Get-ChildItem -LiteralPath (Join-Path $root 'libraries') -Recurse -Filter '*.jar' | ForEach-Object FullName
$plugins=Get-ChildItem -LiteralPath (Join-Path $root 'plugins') -Filter '*.jar' | ForEach-Object FullName
$classpath=(@($libraries)+@($plugins)) -join ';'
$classes=Join-Path $PSScriptRoot 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& $javac --release 25 -encoding UTF-8 -classpath $classpath -d $classes (Join-Path $PSScriptRoot 'CoreHarness.java')
if($LASTEXITCODE -ne 0){throw 'Harness compilation failed'}
& $jar --create --file (Join-Path $PSScriptRoot 'core-harness.jar') -C $classes . -C $PSScriptRoot plugin.yml
if($LASTEXITCODE -ne 0){throw 'Harness packaging failed'}
