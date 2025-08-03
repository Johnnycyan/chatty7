# Build and Run Chatty PowerShell Script
param(
    [switch]$SkipBuild,
    [switch]$WindowsRelease,
    [switch]$WindowsSetup,
    [switch]$AllReleases,
    [switch]$Help
)

# Show help if requested
if ($Help) {
    Write-Host @"
Chatty Build and Run Script

Usage:
    .\build_and_run.ps1                    # Build and run normally
    .\build_and_run.ps1 -SkipBuild         # Skip build, just run existing JAR
    .\build_and_run.ps1 -WindowsRelease    # Create basic release package (ZIP)
    .\build_and_run.ps1 -WindowsSetup      # Create Windows installer (requires extra tools)
    .\build_and_run.ps1 -AllReleases       # Create basic release packages
    .\build_and_run.ps1 -Help              # Show this help

Release packages are created in: build\releases\

Notes:
- Basic release creates a ZIP file with JAR and dependencies
- Windows installer requires Java 8 JDK + javapackager OR Java 14+ JDK + jpackage
- Windows installer also requires InnoSetup for .exe installer creation
- See GitHub documentation for detailed build parameters and tool setup
"@ -ForegroundColor Cyan
    exit 0
}

# Set error action preference to stop on errors
$ErrorActionPreference = "Stop"

Write-Host "Building and running Chatty..." -ForegroundColor Green

# Determine what to build
$buildMode = "normal"
if ($WindowsRelease) { $buildMode = "windowsRelease" }
elseif ($WindowsSetup) { $buildMode = "windowsSetup" }
elseif ($AllReleases) { $buildMode = "allReleases" }

try {
    # Change to script directory
    Set-Location $PSScriptRoot
    
    if (-not $SkipBuild) {
        # Build the project
        Write-Host "Building project..." -ForegroundColor Yellow
        & .\gradlew.bat build
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed with exit code $LASTEXITCODE"
        }
        
        # Create release based on mode
        switch ($buildMode) {
            "windowsRelease" {
                Write-Host "Creating basic release package..." -ForegroundColor Yellow
                & .\gradlew.bat releaseWindows -PjpackagePath="C:\Users\john\AppData\Local\Programs\Eclipse Adoptium\jdk-21.0.8.9-hotspot\bin\jpackage.exe" -PmtPath="C:\Users\john\.nuget\packages\microsoft.windows.sdk.buildtools\10.0.26100.4188\bin\10.0.26100.0\x64\mt.exe"
                if ($LASTEXITCODE -ne 0) {
                    throw "Release creation failed with exit code $LASTEXITCODE"
                }
                Write-Host "Release package created in build\releases\" -ForegroundColor Green
                Write-Host "Note: For Windows standalone executable, additional tools are required." -ForegroundColor Yellow
                return
            }
            "windowsSetup" {
                Write-Host "Creating Windows setup (requires javapackager and InnoSetup)..." -ForegroundColor Yellow
                Write-Host "This requires additional build parameters. Example:" -ForegroundColor Yellow
                Write-Host "gradlew releaseWinSetups -PjavapackagerPath='C:\Program Files\Java\jdk1.8.0_XXX\bin\javapackager.exe'" -ForegroundColor Cyan
                Write-Host "See GitHub documentation for complete setup instructions." -ForegroundColor Yellow
                
                & .\gradlew.bat releaseWinSetups
                if ($LASTEXITCODE -ne 0) {
                    Write-Host "Windows setup creation failed. This requires:" -ForegroundColor Red
                    Write-Host "1. Java 8 JDK with javapackager OR Java 14+ JDK with jpackage" -ForegroundColor Yellow
                    Write-Host "2. InnoSetup for installer creation" -ForegroundColor Yellow
                    Write-Host "3. Microsoft's mt.exe (optional)" -ForegroundColor Yellow
                    Write-Host "Run with proper build parameters as shown in GitHub docs." -ForegroundColor Yellow
                    throw "Windows setup creation failed with exit code $LASTEXITCODE"
                }
                Write-Host "Windows setup created in build\releases\" -ForegroundColor Green
                return
            }
            "allReleases" {
                Write-Host "Creating basic releases..." -ForegroundColor Yellow
                & .\gradlew.bat release
                if ($LASTEXITCODE -ne 0) {
                    throw "Release creation failed with exit code $LASTEXITCODE"
                }
                Write-Host "Basic releases created in build\releases\" -ForegroundColor Green
                Write-Host "Note: Windows standalone and setup require additional tools." -ForegroundColor Yellow
                return
            }
            default {
                # Create the shadowJar (fat JAR with all dependencies) for normal run
                Write-Host "Creating fat JAR with dependencies..." -ForegroundColor Yellow
                & .\gradlew.bat shadowJar
                if ($LASTEXITCODE -ne 0) {
                    throw "ShadowJar creation failed with exit code $LASTEXITCODE"
                }
            }
        }
    }
    
    # Run Chatty only if we're not just creating releases
    if ($buildMode -eq "normal") {
        Write-Host "Starting Chatty..." -ForegroundColor Green
        if (Test-Path ".\build\libs\Chatty.jar") {
            & java -jar ".\build\libs\Chatty.jar"
        } else {
            throw "Chatty.jar not found in build\libs"
        }
    }
    
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
}
