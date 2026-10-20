param([Parameter(Mandatory=$true)][string]$ServerRoot)
$ErrorActionPreference='Stop'
$javac=(Get-Command javac.exe -ErrorAction Stop).Source
$jar=Join-Path (Split-Path $javac -Parent) 'jar.exe'
$root=(Resolve-Path -LiteralPath $ServerRoot).Path
$libs=Get-ChildItem -LiteralPath (Join-Path $root 'libraries') -Recurse -Filter '*.jar' | ForEach-Object FullName
$plugins=Get-ChildItem -LiteralPath (Join-Path $root 'plugins') -Filter '*.jar' | ForEach-Object FullName
$classpath=(@($libs)+@($plugins)) -join ';'
$build=Join-Path $PSScriptRoot 'target\display-harness'
$classes=Join-Path $build 'classes';$meta=Join-Path $build 'meta'
New-Item -ItemType Directory -Path $classes,$meta -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'display-plugin.yml') -Destination (Join-Path $meta 'plugin.yml') -Force
& $javac --release 25 -encoding UTF-8 -classpath $classpath -d $classes (Join-Path $PSScriptRoot 'DisplayHarness.java')
if($LASTEXITCODE -ne 0){throw 'Display harness compilation failed'}
& $jar --create --file (Join-Path $build 'display-harness.jar') -C $classes . -C $meta plugin.yml
if($LASTEXITCODE -ne 0){throw 'Display harness packaging failed'}
