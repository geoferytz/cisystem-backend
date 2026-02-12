# ==========================================
# CISYSTEM BACKEND CI/CD DEPLOYMENT
# PowerShell (Windows → AWS Ubuntu Linux)
# ==========================================

$ErrorActionPreference = "Stop"

trap {
    Write-Host "[-] Unhandled error: $($_.Exception.Message)"
    exit 1
}

function Assert-LastExitCode {
    param(
        [string]$Step
    )
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[-] Failed step: $Step (exit code: $LASTEXITCODE)"
        exit $LASTEXITCODE
    }
}

Write-Host "=========================================="
Write-Host "CISYSTEM BACKEND DEPLOYMENT STARTED"
Write-Host "=========================================="

# --------------------------------------------------
# CHECK DOCKER MODE (must be Linux containers)
# --------------------------------------------------
Write-Host "[*] Checking Docker mode..."
$dockerInfo = docker info 2>&1 | Out-String
Assert-LastExitCode "docker info"

if ($dockerInfo -like "*OSType: windows*") {
    Write-Host "[-] Docker is in Windows container mode!"
    Write-Host "Right-click Docker Desktop → Switch to Linux containers"
    exit 1
}
Write-Host "[+] Docker mode OK"

# --------------------------------------------------
# CONFIGURATION
# --------------------------------------------------
$ImageName         = "geofrey2025/cisystem-backend"
$VersionFile       = ".backend-version"
$PemPath           = 'C:\Users\geofr\.ssh\timerz.pem'
$AwsUser           = "ubuntu"
$AwsHost           = "ec2-16-170-25-198.eu-north-1.compute.amazonaws.com"
$DeploymentDir     = "/home/ubuntu/deployment"

$DockerHubUsername = "geofrey2025"
$DockerHubPassword = "Timerz@2026"

# --------------------------------------------------
# VERSION INCREMENT
# --------------------------------------------------
if (!(Test-Path $VersionFile)) {
    "1.0.0" | Set-Content $VersionFile
}

$version   = Get-Content $VersionFile
$parts     = $version.Split(".")
$parts[2]  = [int]$parts[2] + 1
$newVersion = "$($parts[0]).$($parts[1]).$($parts[2])"
Set-Content $VersionFile $newVersion

Write-Host "[+] New version: $newVersion"


# --------------------------------------------------
# BUILD SPRING BOOT JAR
# --------------------------------------------------
Write-Host "[*] Building Spring Boot project..."
./mvnw clean package -DskipTests
Assert-LastExitCode "Maven build"
Write-Host "[+] Maven build successful"

# --------------------------------------------------
# CLEAN OLD DOCKER IMAGES
# --------------------------------------------------
Write-Host "[*] Cleaning old Docker images..."
$oldImages = docker images --filter "reference=$ImageName*" -q 2>$null
if ($oldImages) {
    $oldImages | ForEach-Object {
        try { docker rmi $_ -f 2>$null | Out-Null } catch {}
    }
}
docker image prune -f 2>$null | Out-Null
Write-Host "[+] Cleanup done"

# --------------------------------------------------
# DOCKER LOGIN
# --------------------------------------------------
Write-Host "[*] Logging into Docker Hub..."
$DockerHubPassword | docker login -u $DockerHubUsername --password-stdin
Assert-LastExitCode "Docker login"
Write-Host "[+] Docker login successful"

# --------------------------------------------------
# BUILD DOCKER IMAGE
# --------------------------------------------------
$tagVersion = "${ImageName}:${newVersion}"
$tagCurrent = "${ImageName}:current"

$env:DOCKER_BUILDKIT = "1"

Write-Host "[*] Building Docker image: $tagVersion"
docker build --pull -t $tagVersion .
Assert-LastExitCode "Docker build"
Write-Host "[+] Docker build complete"

# Verify platform
$imageInfo = docker inspect $tagVersion --format='OS: {{.Os}}, Arch: {{.Architecture}}'
Write-Host "[*] Image platform: $imageInfo"

# Tag as current
docker tag $tagVersion $tagCurrent

# --------------------------------------------------
# PUSH TO DOCKER HUB
# --------------------------------------------------
Write-Host "[*] Pushing images to Docker Hub..."
docker push $tagVersion
Assert-LastExitCode "Docker push version"

docker push $tagCurrent
Assert-LastExitCode "Docker push current"
Write-Host "[+] Images pushed successfully"

# --------------------------------------------------
# CREATE ENV FILE
# --------------------------------------------------
$envFilePath = ".env.backend"
"BACKEND_VERSION=$newVersion" | Set-Content -NoNewline $envFilePath
Write-Host "[+] .env.backend created"

# --------------------------------------------------
# COPY ENV FILE TO AWS
# --------------------------------------------------
Write-Host "[*] Uploading .env.backend to AWS..."
scp -i $PemPath $envFilePath "${AwsUser}@${AwsHost}:${DeploymentDir}/.env.backend"
Assert-LastExitCode "SCP env"
Write-Host "[+] File copied to server"

# --------------------------------------------------
# CREATE REMOTE DEPLOY SCRIPT (LINUX SAFE)
# --------------------------------------------------
$remoteScript = @"
#!/usr/bin/env bash
set -e


cd $DeploymentDir

echo "=== Pulling latest backend image ==="
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend pull backend

echo "=== Restarting backend container ==="
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend up -d --force-recreate backend

echo "=== Running containers ==="
docker ps --filter name=backend

echo "=== Last backend logs ==="
docker ps --filter "name=backend" --format "{{.Names}}" | while read cname; do
    docker logs "$cname" --tail 40
done

"@

$tempFile = "deploy_remote.sh"

# CRITICAL: Write Linux-compatible LF file (no CRLF, no BOM)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$remoteScript = $remoteScript -replace "`r",""
$remoteScript = $remoteScript.TrimStart()

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($tempFile, $remoteScript, $utf8NoBom)


# Upload script
Write-Host "[*] Uploading remote deploy script..."
scp -i $PemPath $tempFile "${AwsUser}@${AwsHost}:${DeploymentDir}/deploy_remote.sh"
Assert-LastExitCode "SCP script"
Write-Host "[+] Remote deploy script uploaded"

# Execute script
Write-Host "[*] Running deployment on AWS..."
ssh -i $PemPath -o StrictHostKeyChecking=no "${AwsUser}@${AwsHost}" "chmod +x $DeploymentDir/deploy_remote.sh && $DeploymentDir/deploy_remote.sh"
Assert-LastExitCode "SSH deploy"

Write-Host ""
Write-Host "=========================================="
Write-Host "[+] DEPLOYMENT COMPLETED SUCCESSFULLY"
Write-Host "[+] VERSION: $newVersion"
Write-Host "=========================================="
