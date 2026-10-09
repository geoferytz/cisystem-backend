# CISYSTEM BACKEND - DEPLOY TO CONTABO
#
#   .\deploy.ps1                 # bump patch, build, push, deploy
#   .\deploy.ps1 -Bump minor     # or: major
#   .\deploy.ps1 -Bump none      # deploy the version in .backend-version as is (first deploy)
#   .\deploy.ps1 -BuildOnly      # bump patch, build, push - and STOP. The server is never
#                                # contacted. Test that image on staging, then deploy the
#                                # SAME image with -DeployOnly <version>.
#   .\deploy.ps1 -DeployOnly 1.0.32
#                                # NO build/push: deploy an image already on Docker Hub
#
# Flow (same idea as the LSMS deploy-v3 script):
#   PC:     bump version -> mvnw clean package -> docker build -> docker push (exact tag, never :current/:latest)
#   Server: pull -> write BACKEND_VERSION into .env.backend -> compose up backend -> health check
#           -> automatic rollback on failure (remote-deploy.sh)
#   PC:     .backend-version is written + committed ONLY after DEPLOY_OK.
# The frontend and the DB are never touched.
param(
    [ValidateSet('patch', 'minor', 'major', 'none')]
    [string]$Bump = 'patch',
    # Build and push the image, then stop: no SSH, no deploy, no version file.
    # Refuses a tag that is already on Docker Hub (a pushed image is never overwritten).
    [switch]$BuildOnly,
    # Deploy an image that is already on Docker Hub (built earlier with -BuildOnly and tested
    # on staging). Skips the build and the push.
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$DeployOnly
)

$ErrorActionPreference = 'Stop'

# -- CONFIG ---------------------------------------------------
$ImageName    = 'geofrey2025/cisystem-backend'
$VersionFile  = '.backend-version'
$SshHost      = 'root@164.68.122.5'               # Contabo. Password-free? ssh-copy-id root@164.68.112.5
$RemoteScript = '/tmp/remote-deploy-backend.sh'

function Step([string]$m) { Write-Host "[*] $m" -ForegroundColor Cyan }
function Fail([string]$m) { Write-Host "`n[FAILED] $m" -ForegroundColor Red; exit 1 }

# Run a native command and stop on a non-zero exit code - after EVERY command.
function Invoke-Checked([string]$What, [scriptblock]$Cmd) {
    & $Cmd
    if ($LASTEXITCODE -ne 0) { Fail "$What (exit $LASTEXITCODE)" }
}

# Is this exact tag already on Docker Hub? "no such manifest" goes to stderr - that is the answer,
# not an error, so it must not trip $ErrorActionPreference = 'Stop'.
function Test-ImageOnHub([string]$Image) {
    $ErrorActionPreference = 'Continue'
    docker manifest inspect $Image 2>&1 | Out-Null
    return ($LASTEXITCODE -eq 0)
}

# sha256 digest of a local image that has been pushed or pulled ('' when it has none).
# No quotes inside the Go template: Windows PowerShell 5.1 strips them from native arguments.
function Get-ImageDigest([string]$Image) {
    $meta = (docker image inspect $Image --format '{{json .}}') -join '' | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $meta) { return '' }
    return ((@($meta.RepoDigests) | Select-Object -First 1) -replace '^.*@', '')
}

# Refuse ANY modified, staged, deleted or untracked path in the repo.
# No flag skips it. Runs git against $RepoRoot explicitly (not the caller's cwd) and forces
# untracked files to be listed, whatever the user's git config says.
function Assert-CleanTree([string]$Root) {
    $ErrorActionPreference = 'Continue'   # git may print CRLF warnings on stderr; they are not errors
    # Refresh stat info first, so a file whose content is unchanged (e.g. only touched, or CRLF/LF
    # normalisation) never reads 'modified' on one run and clean on the next: same answer every run.
    git -C $Root update-index -q --refresh | Out-Null
    $lines = @(git -C $Root -c core.quotepath=false status --porcelain=v1 --untracked-files=all --ignore-submodules=none)
    if ($LASTEXITCODE -ne 0) { Fail "git status imeshindwa kwenye $Root (exit $LASTEXITCODE)." }
    if ($lines.Count) {
        Fail ("Working tree si safi ($($lines.Count)) - commit au ondoa kwanza:`n    " + ($lines -join "`n    "))
    }
}

$RepoRoot = $PSScriptRoot
Push-Location $RepoRoot
$tempScript = $null
try {
    if ($BuildOnly -and $DeployOnly) { Fail "Tumia -BuildOnly AU -DeployOnly, si vyote viwili." }
    if ($DeployOnly -and $PSBoundParameters.ContainsKey('Bump')) { Fail "Tumia -DeployOnly AU -Bump, si vyote viwili." }

    # -- CHECKS (nothing is changed yet) ----------------------
    if (-not (Test-Path $VersionFile)) { Fail "$VersionFile haipo (inatakiwa iwe na semver, mfano 1.0.0)." }
    $current = (Get-Content $VersionFile -Raw).Trim()
    if ($current -notmatch '^(\d+)\.(\d+)\.(\d+)$') { Fail "$VersionFile si semver: '$current'" }
    $major = [int]$Matches[1]; $minor = [int]$Matches[2]; $patch = [int]$Matches[3]
    switch ($Bump) {
        'major' { $major++; $minor = 0; $patch = 0 }
        'minor' { $minor++; $patch = 0 }
        'patch' { $patch++ }
        'none'  { }
    }
    $newVersion = "$major.$minor.$patch"
    if ($DeployOnly) { $newVersion = $DeployOnly }
    $image = "${ImageName}:${newVersion}"

    Assert-CleanTree $RepoRoot

    # Docker must be running AND in Linux-container mode (the image runs on Ubuntu).
    Invoke-Checked "Docker haipatikani - washa Docker Desktop" { docker version --format '{{.Server.Version}}' | Out-Null }
    $dockerOs = (docker info --format '{{.OSType}}' 2>$null)
    if ($dockerOs -eq 'windows') { Fail "Docker iko Windows-container mode - switch to Linux containers." }

    if ($BuildOnly) {
        # Nothing on the server is read or changed. A tag that is already published is never rebuilt:
        # what was tested on staging must be byte-for-byte what -DeployOnly later puts on prod.
        if (Test-ImageOnHub $image) { Fail "$image tayari iko Docker Hub - haitaandikwa upya. Tumia -DeployOnly $newVersion kuideploy, au -Bump kwa toleo jipya." }
        Step "-BuildOnly: build + push ya $image. Server HAIGUSWI; $VersionFile haibadilishwi."
    } else {
        # Hakuna BatchMode: password prompt inaruhusiwa. Prompt moja hapa, moja kwa scp, moja kwa deploy.
        # Kama unataka zero prompts: ssh-copy-id $SshHost (au alias 'contabo' kwenye ~/.ssh/config).
        Invoke-Checked "SSH kwenda '$SshHost' imeshindwa (jaribu: ssh $SshHost)" {
            ssh -o ConnectTimeout=15 $SshHost "true"
        }
    }

    Step "Toleo: $current -> $newVersion   ($image)"

    if ($DeployOnly) {
        # The image must already be on Docker Hub; it is deployed exactly as it is.
        if (-not (Test-ImageOnHub $image)) { Fail "$image haipo Docker Hub (au hujaingia: docker login -u geofrey2025). Server haijaguswa." }
        Invoke-Checked "docker pull $image imeshindwa. Server haijaguswa." { docker pull -q $image | Out-Null }
        Step "-DeployOnly: build na push vimerukwa. Image: $(Get-ImageDigest $image)"
    } else {
        # -- BUILD SPRING BOOT JAR ------------------------------
        Step "mvnw clean package -DskipTests ..."
        Invoke-Checked "Maven build imeshindwa" { .\mvnw.cmd clean package -DskipTests }

        # -- BUILD DOCKER IMAGE ---------------------------------
        Step "docker build..."
        $revision = (git -C $RepoRoot rev-parse HEAD).Trim()
        Invoke-Checked "docker build imeshindwa" {
            docker build --build-arg "APP_VERSION=$newVersion" --label "org.opencontainers.image.revision=$revision" -t $image .
        }

        # JVM check inside the image before it is pushed. --entrypoint java overrides
        # "java -jar app.jar" - tunapima JVM tu, sio app (app ingeangalia DB isiyopo hapa).
        Invoke-Checked "java -version ndani ya image imeshindwa" { docker run --rm --entrypoint java $image -version }

        # -- PUSH (exact tag only; Docker Hub sometimes times out -> retry) --
        Step "docker push $image ..."
        $pushed = $false
        foreach ($wait in 0, 5, 15) {
            if ($wait) { Write-Host "    push imeshindwa - najaribu tena baada ya ${wait}s" -ForegroundColor DarkYellow; Start-Sleep -Seconds $wait }
            docker push $image
            if ($LASTEXITCODE -eq 0) { $pushed = $true; break }
        }
        if (-not $pushed) { Fail "docker push imeshindwa. Umeingia Docker Hub? (docker login -u geofrey2025). Server haijaguswa." }
    }   # end: not -DeployOnly

    if ($BuildOnly) {
        Assert-CleanTree $RepoRoot   # nothing may have changed while building: the image IS this commit
        Write-Host ""
        Write-Host "[OK] BUILD_OK $newVersion - imejengwa na kusukumwa. Server haijaguswa; $VersionFile bado ni $current." -ForegroundColor Green
        Write-Host "  Image:    $image"
        Write-Host "  Digest:   $(Get-ImageDigest $image)"
        Write-Host "  Commit:   $(git log -1 --format='%h %s')"
        Write-Host "  Kifuatacho: ijaribu staging, kisha"
        Write-Host "      .\deploy.ps1 -DeployOnly $newVersion"
        return
    }

    # -- REMOTE DEPLOY ----------------------------------------
    Assert-CleanTree $RepoRoot   # again: nothing may have changed while building

    # LF-only copy without BOM (CRLF / BOM break bash).
    $body = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'remote-deploy.sh')) -replace "`r", ''
    $tempScript = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tempScript, $body, [System.Text.UTF8Encoding]::new($false))

    Step "Deploying kwenye $SshHost ..."
    Invoke-Checked "scp ya script imeshindwa. Server haijaguswa." { scp -q $tempScript "${SshHost}:${RemoteScript}" }

    $out = @()
    ssh $SshHost "bash $RemoteScript $newVersion; rc=`$?; rm -f $RemoteScript; exit `$rc" |
        ForEach-Object { Write-Host "    $_"; $out += $_ }
    $sshExit = $LASTEXITCODE

    if ($sshExit -ne 0 -or ($out -notcontains "DEPLOY_OK $newVersion")) {
        Fail "Deploy ya $newVersion imeshindwa (ssh exit $sshExit). Soma mistari ya [backend] hapo juu - rollback imefanyika yenyewe kama kulikuwa na toleo la awali. $VersionFile haijabadilishwa."
    }

    # -- SUCCESS: only now record the version ------------------
    [System.IO.File]::WriteAllText((Join-Path $RepoRoot $VersionFile), "$newVersion`n", [System.Text.ASCIIEncoding]::new())
    git add -- $VersionFile
    git diff --cached --quiet -- $VersionFile
    if ($LASTEXITCODE -ne 0) {
        git commit --quiet --only -m "deploy: backend $newVersion" -- $VersionFile
        if ($LASTEXITCODE -ne 0) { Write-Host "[!] Deploy imefanikiwa lakini git commit ya $VersionFile imeshindwa - commit kwa mkono." -ForegroundColor DarkYellow }
        else { Step "$VersionFile = $newVersion (committed)" }
    }

    # Old local images of this repository (keep new + previous).
    docker images $ImageName --format '{{.Tag}}' |
        Where-Object { $_ -ne $newVersion -and $_ -ne $current -and $_ -ne '<none>' } |
        ForEach-Object { docker rmi "${ImageName}:$_" | Out-Null }

    Write-Host "`n[OK] DEPLOY_OK $newVersion" -ForegroundColor Green
}
finally {
    if ($tempScript -and (Test-Path $tempScript)) { Remove-Item $tempScript -Force }
    Pop-Location
}
