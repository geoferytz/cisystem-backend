# ==========================================
# CISYSTEM BACKEND CI/CD DEPLOYMENT
# PowerShell (Windows → AWS Ubuntu Linux)
# ==========================================

$ErrorActionPreference = "Stop"

Write-Host "=========================================="
Write-Host "CISYSTEM BACKEND DEPLOYMENT STARTED"
Write-Host "=========================================="

# --------------------------------------------------
# CHECK DOCKER MODE (must be Linux containers)
# --------------------------------------------------
Write-Host "[*] Checking Docker mode..."
$dockerInfo = docker info 2>&1 | Out-String
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
$DeploymentDir     = "~/deployment"

$DockerHubUsername = "geofrey2025"
$DockerHubPassword = "Timerz@2026"

# --------------------------------------------------
# VERSION INCREMENT
# --------------------------------------------------
if (!(Test-Path $VersionFile)) {
    "1.0.0" | Out-File $VersionFile
}

$version   = Get-Content $VersionFile
$parts     = $version.Split(".")
$parts[2]  = [int]$parts[2] + 1
$newVersion = "$($parts[0]).$($parts[1]).$($parts[2])"
$newVersion | Out-File $VersionFile

Write-Host "[+] New version: $newVersion"

# --------------------------------------------------
# BUILD SPRING BOOT JAR
# --------------------------------------------------
Write-Host "[*] Building Spring Boot project..."
./mvnw clean package -DskipTests
Write-Host "[+] Maven build successful"

# --------------------------------------------------
# CLEAN OLD DOCKER IMAGES
# --------------------------------------------------
Write-Host "[*] Cleaning old Docker images..."
$oldImages = docker images --filter "reference=$ImageName*" -q 2>$null
if ($oldImages) {
    $oldImages | ForEach-Object {
        try {
            docker rmi $_ -f 2>$null | Out-Null
        } catch {
        }
    }
}
try {
    docker system prune -af 2>$null | Out-Null
} catch {
}
Write-Host "[+] Cleanup done"

# --------------------------------------------------
# DOCKER LOGIN
# --------------------------------------------------
Write-Host "[*] Logging into Docker Hub..."
$DockerHubPassword | docker login -u $DockerHubUsername --password-stdin
Write-Host "[+] Docker login successful"

# --------------------------------------------------
# BUILD DOCKER IMAGE
# --------------------------------------------------
$tagVersion = "${ImageName}:${newVersion}"
$tagCurrent = "${ImageName}:current"

Write-Host "[*] Building Docker image: $tagVersion"
docker build --no-cache --pull -t $tagVersion .
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
docker push $tagCurrent
Write-Host "[+] Images pushed successfully"

# --------------------------------------------------
# CREATE ENV FILE
# --------------------------------------------------
$envFilePath = ".env.backend"
"BACKEND_VERSION=$newVersion" | Out-File -Encoding ascii $envFilePath
Write-Host "[+] .env.backend created"

# --------------------------------------------------
# COPY ENV FILE TO AWS
# --------------------------------------------------
Write-Host "[*] Uploading .env.backend to AWS..."
scp -i $PemPath $envFilePath "${AwsUser}@${AwsHost}:${DeploymentDir}/.env.backend"
Write-Host "[+] File copied to server"

# --------------------------------------------------
# CREATE REMOTE DEPLOY SCRIPT
# --------------------------------------------------
$remoteScript = @"
#!/bin/bash
set -e

cd $DeploymentDir

# Convert Windows line endings to Linux
sed -i 's/\r\$//' .env*

# Pull latest backend image
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend pull backend

# Restart backend container
docker compose --env-file .env --env-file .env.backend --env-file .env.frontend up -d --force-recreate backend

# List running backend containers
docker ps --filter name=backend

# Show recent backend logs
docker logs backend --tail 40
"@

$tempFile = "deploy_remote.sh"
$remoteScript | Out-File -Encoding ascii $tempFile

# Upload remote deploy script
Write-Host "[*] Uploading remote deploy script..."
scp -i $PemPath $tempFile "${AwsUser}@${AwsHost}:${DeploymentDir}/deploy_remote.sh"
Write-Host "[+] Remote deploy script uploaded"

# Execute remote deploy script
Write-Host "[*] Running deployment on AWS..."
ssh -i $PemPath -o StrictHostKeyChecking=no "${AwsUser}@${AwsHost}" "bash $DeploymentDir/deploy_remote.sh"

Write-Host ""
Write-Host "=========================================="
Write-Host "[+] DEPLOYMENT COMPLETED SUCCESSFULLY"
Write-Host "[+] VERSION: $newVersion"
Write-Host "=========================================="
