pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = 'asset-allocation-portal'
        TEST_PORT = '8082'
        APP_PORT = '8081'
        CONTAINER_NAME = 'asset-portal'
        DB_URL = 'jdbc:postgresql://localhost:5432/assetdb'
        DOCKER_EXE = 'C:\\Users\\Sahil Mahadik\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'
        JAR_NAME = 'asset-allocation-portal-0.0.1-SNAPSHOT.jar'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Spring Boot App') {
            steps {
                bat 'mvn -B clean package'
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'target/asset-allocation-portal-0.0.1-SNAPSHOT.jar',
                                 fingerprint: true
            }
        }

        stage('Start Test Application') {
            steps {
                bat '''
                    @echo off
                    setlocal

                    set "JAR=target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar"

                    if not exist "%JAR%" (
                        echo ERROR: Application JAR not found.
                        exit /b 1
                    )

                    echo Starting test application on port 8082...

                    start "AssetTestApp" /B cmd /c "java -Duser.timezone=UTC -jar %JAR% --server.port=8082 --spring.datasource.url=jdbc:postgresql://localhost:5432/assetdb > test-app.log 2>&1"

                    powershell.exe -NoProfile -Command ^
                    "$deadline = (Get-Date).AddSeconds(90); $ready = $false; while ((Get-Date) -lt $deadline) { try { $r = Invoke-WebRequest 'http://127.0.0.1:8082/' -UseBasicParsing -TimeoutSec 3; if ($r.StatusCode -eq 200) { $ready = $true; break } } catch {}; Start-Sleep -Seconds 2 }; if (-not $ready) { Get-Content 'test-app.log' -Tail 40 -ErrorAction SilentlyContinue; exit 1 }"

                    if errorlevel 1 (
                        echo ERROR: Application did not become ready.
                        exit /b 1
                    )

                    echo Test application is ready.
                    exit /b 0
                '''
            }
        }

        stage('Run Selenium Tests') {
            steps {
                dir('selenium-tests') {
                    bat 'mvn -B test "-Dapp.url=http://127.0.0.1:8082/"'
                }
            }
        }

        stage('Stop Test Application') {
            steps {
                powershell '''
                    $ErrorActionPreference = 'Continue'

                    $pidFile = Join-Path $env:WORKSPACE 'test-app.pid'

                    if (Test-Path $pidFile) {
                        $appPid = [int](Get-Content $pidFile -Raw).Trim()

                        # Verify the PID belongs to the expected Java test app.
                        $proc = Get-CimInstance Win32_Process `
                            -Filter "ProcessId = $appPid" `
                            -ErrorAction SilentlyContinue

                        if ($proc -and
                            $proc.Name -eq 'java.exe' -and
                            $proc.CommandLine -match 'server.port=8082') {
                            Write-Host "Stopping test application PID $appPid..."
                            Stop-Process -Id $appPid -Force `
                                -ErrorAction SilentlyContinue
                        }
                        else {
                            Write-Host 'Test application process is already stopped.'
                        }

                        Remove-Item $pidFile -Force `
                            -ErrorAction SilentlyContinue
                    }
                    else {
                        Write-Host 'No test application PID file found.'
                    }
                '''
            }
        }

        stage('Verify Docker Access') {
            steps {
                bat '"%DOCKER_EXE%" version'
            }
        }

        stage('Build Docker Image') {
            steps {
                bat '"%DOCKER_EXE%" build --tag %APP_NAME%:%BUILD_NUMBER% .'
            }
        }

        stage('Deploy Docker Container') {
            steps {
                powershell '''
                    $ErrorActionPreference = 'Stop'

                    $docker = $env:DOCKER_EXE
                    $image = "$env:APP_NAME`:$env:BUILD_NUMBER"
                    $container = $env:CONTAINER_NAME

                    Write-Host "Deploying image: $image"

                    # Confirm the image exists before replacing the container.
                    & $docker image inspect $image
                    if ($LASTEXITCODE -ne 0) {
                        throw "Docker image not found: $image"
                    }

                    # Remove only the application container.
                    & $docker rm --force $container 2>$null
                    # A missing old container is acceptable.

                    & $docker run -d `
                        --name $container `
                        --restart unless-stopped `
                        -p 8081:8081 `
                        -e 'SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/assetdb' `
                        $image

                    if ($LASTEXITCODE -ne 0) {
                        throw 'Failed to start the application container.'
                    }

                    $ready = $false

                    for ($i = 0; $i -lt 30; $i++) {
                        Start-Sleep -Seconds 2

                        try {
                            $response = Invoke-WebRequest `
                                -Uri 'http://127.0.0.1:8081/' `
                                -TimeoutSec 3 `
                                -UseBasicParsing

                            if ($response.StatusCode -eq 200) {
                                $ready = $true
                                break
                            }
                        }
                        catch {
                            # Container may still be starting.
                        }

                        $running = & $docker inspect `
                            --format '{{.State.Running}}' $container 2>$null

                        if ($running -ne 'true') {
                            & $docker logs --tail 60 $container
                            throw 'Application container exited during startup.'
                        }
                    }

                    if (!$ready) {
                        & $docker logs --tail 60 $container
                        throw 'Deployment health check failed on port 8081.'
                    }

                    Write-Host "Deployment successful: http://localhost:8081/"
                '''
            }
        }
    }

    post {
        always {
            powershell '''
                $ErrorActionPreference = 'Continue'

                $pidFile = Join-Path $env:WORKSPACE 'test-app.pid'

                if (Test-Path $pidFile) {
                    $appPid = 0

                    try {
                        $appPid = [int](Get-Content $pidFile -Raw).Trim()
                    }
                    catch {}

                    if ($appPid -gt 0) {
                        $proc = Get-CimInstance Win32_Process `
                            -Filter "ProcessId = $appPid" `
                            -ErrorAction SilentlyContinue

                        if ($proc -and
                            $proc.Name -eq 'java.exe' -and
                            $proc.CommandLine -match 'server.port=8082') {
                            Stop-Process -Id $appPid -Force `
                                -ErrorAction SilentlyContinue
                            Write-Host "Cleaned up test application PID $appPid."
                        }
                    }

                    Remove-Item $pidFile -Force `
                        -ErrorAction SilentlyContinue
                }
            '''

            script {
                if (currentBuild.currentResult == 'SUCCESS') {
                    echo 'Pipeline completed successfully.'
                } else {
                    echo "Pipeline finished with status: ${currentBuild.currentResult}"
                }
            }
        }
    }
}