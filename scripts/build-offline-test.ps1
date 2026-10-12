param(
    [Parameter(Mandatory=$true)][string]$NdkPath,
    [Parameter(Mandatory=$true)][string]$CMakePath,
    [Parameter(Mandatory=$true)][string]$NinjaPath,
    [string]$LlamaSourcePath = '',
    [string]$GradlePath = '',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
$vendorPath = Join-Path $repoPath 'native/.vendor'
$modelPath = Join-Path $repoPath 'app/embedded-assets/models/MiniCPM5-1B-Q4_K_M.gguf'
$modelHash = '81b64d05a23b17b34c475f42b3e72fbde62d4b92cc34541f7a8031d0752deafa'
$modelRevision = '3d55fac80935ae6456986ad2384b5cbcc4d6c948'
$llamaRevision = '77757a34672a888845a09ab45728b8d34aa475ff'
New-Item -ItemType Directory -Force -Path $vendorPath,(Split-Path $modelPath) | Out-Null

function Assert-Hash([string]$Path, [string]$Expected) {
    if (!(Test-Path -LiteralPath $Path) -or (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Expected) {
        throw "SHA-256 verification failed: $Path"
    }
}
function Fetch([string]$Url, [string]$Destination) {
    if ($Offline) { throw "Missing local dependency in offline build: $Destination" }
    & curl.exe -L --fail --retry 8 --retry-all-errors --connect-timeout 30 -o $Destination $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
}
if (!(Test-Path -LiteralPath $modelPath)) {
    $partialPath = "$modelPath.part"
    Fetch "https://huggingface.co/openbmb/MiniCPM5-1B-GGUF/resolve/$modelRevision/MiniCPM5-1B-Q4_K_M.gguf?download=true" $partialPath
    Assert-Hash $partialPath $modelHash
    Move-Item -LiteralPath $partialPath -Destination $modelPath
}
Assert-Hash $modelPath $modelHash
if (!$LlamaSourcePath) {
    $LlamaSourcePath = Join-Path $vendorPath "llama.cpp-$llamaRevision"
    if (!(Test-Path -LiteralPath (Join-Path $LlamaSourcePath 'CMakeLists.txt'))) {
        $archivePath = Join-Path $vendorPath 'llama-source.zip'
        Fetch "https://codeload.github.com/ggml-org/llama.cpp/zip/$llamaRevision" $archivePath
        Assert-Hash $archivePath 'd042caa5cee159ab19e02b1903ec140383c81952b3943033511b606213a6506b'
        Expand-Archive -LiteralPath $archivePath -DestinationPath $vendorPath -Force
    }
}
$NdkPath = (Resolve-Path -LiteralPath $NdkPath).Path
$LlamaSourcePath = (Resolve-Path -LiteralPath $LlamaSourcePath).Path
$CMakePath = (Resolve-Path -LiteralPath $CMakePath).Path
$NinjaPath = (Resolve-Path -LiteralPath $NinjaPath).Path
$buildPath = Join-Path $repoPath 'native/.build'
& $CMakePath -S (Join-Path $repoPath 'native') -B $buildPath -G Ninja "-DCMAKE_MAKE_PROGRAM=$NinjaPath" `
    "-DCMAKE_TOOLCHAIN_FILE=$NdkPath/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a `
    -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release "-DLLAMA_SOURCE_DIR=$LlamaSourcePath"
if ($LASTEXITCODE -ne 0) { throw 'Native configuration failed' }
& $CMakePath --build $buildPath --config Release --parallel 6
if ($LASTEXITCODE -ne 0) { throw 'Native compilation failed' }
$jniPath = Join-Path $repoPath 'app/embedded-jni/arm64-v8a'
New-Item -ItemType Directory -Force -Path $jniPath | Out-Null
Copy-Item -LiteralPath (Join-Path $buildPath 'liblexiforge_llama.so') -Destination $jniPath
& (Join-Path $NdkPath 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe') --strip-unneeded (Join-Path $jniPath 'liblexiforge_llama.so')
if ($LASTEXITCODE -ne 0) { throw 'Native symbol stripping failed' }
if (!$GradlePath) { $GradlePath = Join-Path $repoPath 'gradlew.bat' }
$gradleArgs = @(':app:assembleDebug', '-PembeddedModel=true', '--no-daemon', '--no-configuration-cache', '--console=plain')
if ($Offline) { $gradleArgs += '--offline' }
Push-Location $repoPath
try {
    & $GradlePath @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
    Write-Host "Embedded model APK: $repoPath/app/build/outputs/apk/debug/app-debug.apk"
} finally { Pop-Location }
