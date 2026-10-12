pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 15, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    tools {
        jdk 'JDK-21'
        maven 'Maven-3.9.16'
    }

    environment {
        APP_JAR = 'target/asset-allocation-portal-0.0.1-SNAPSHOT.jar'
        TEST_PORT = '8082'
        DEPLOY_PORT = '8081'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                bat 'git status --short --branch'
            }
        }

        stage('Build Spring Boot App') {
            steps {
                timeout(time: 3, unit: 'MINUTES') {
                    bat 'mvn -B clean package'
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts(
                    artifacts: 'target/*.jar',
                    fingerprint: true
                )
            }
        }

        stage('Start Test Application') {
            steps {
                timeout(time: 90, unit: 'SECONDS') {
                    powershell '''
                        $listener = Get-NetTCPConnection `
                            -LocalPort 8082 -State Listen `
                            -ErrorAction SilentlyContinue |
                            Select-Object -First 1

                        if ($listener) {
                            throw 'Port 8082 is already occupied.'
                        }
                    '''

                    bat '''
                        start "AssetTestApp" /B cmd /c "java ""-Duser.timezone=UTC"" -jar target\\asset-allocation-portal-0.0.1-SNAPSHOT.jar --server.port=8082 > test-app.log 2>&1"
                    '''

                    powershell '''
                        $ready = $false

                        for ($i = 0; $i -lt 30; $i++) {
                            Start-Sleep -Seconds 2

                            try {
                                $r = Invoke-WebRequest `
                                    -Uri 'http://127.0.0.1:8082/' `
                                    -TimeoutSec 2 -UseBasicParsing

                                if ($r.StatusCode -eq 200) {
                                    $ready = $true
                                    break
                                }
                            } catch {}
                        }

                        if (!$ready) {
                            Get-Content 'test-app.log' `
                                -ErrorAction SilentlyContinue
                            throw 'Test application failed to start on port 8082.'
                        }

                        $listener = Get-NetTCPConnection `
                            -LocalPort 8082 -State Listen `
                            -ErrorAction SilentlyContinue |
                            Select-Object -First 1

                        if ($listener) {
                            Set-Content 'test-app.pid' $listener.OwningProcess
                        }

                        Write-Host 'Test application is ready on port 8082.'
                    '''
                }
            }
        }

        stage('Run Selenium Tests') {
            steps {
                timeout(time: 4, unit: 'MINUTES') {
                    dir('selenium-tests') {
                        bat 'mvn -B test "-Dapp.url=http://127.0.0.1:8082/"'
                    }
                }
            }

            post {
                always {
                    junit(
                        allowEmptyResults: true,
                        testResults: 'selenium-tests/target/surefire-reports/*.xml'
                    )
                }
            }
        }

        stage('Stop Test Application') {
            steps {
                powershell '''
                    if (Test-Path 'test-app.pid') {
                        $rawPid = (Get-Content 'test-app.pid' -Raw).Trim()
                        $appPid = 0

                        if ([int]::TryParse($rawPid, [ref]$appPid)) {
                            $p = Get-CimInstance Win32_Process `
                                -Filter "ProcessId = $appPid" `
                                -ErrorAction SilentlyContinue

                            if ($p -and
                                $p.Name -eq 'java.exe' -and
                                $p.CommandLine -match 'asset-allocation-portal' -and
                                $p.CommandLine -match '--server.port=8082') {

                                Stop-Process -Id $appPid -Force `
                                    -ErrorAction SilentlyContinue

                                Write-Host "Stopped test application PID $appPid."
                            }
                        }

                        Remove-Item 'test-app.pid' -Force `
                            -ErrorAction SilentlyContinue
                    }
                '''
            }
        }

        stage('Verify Docker Access') {
    steps {
        bat '"C:\\Users\\Sahil Mahadik\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe" version'
    }
}

        
stage('Build Docker Image') {
    steps {
        timeout(time: 5, unit: 'MINUTES') {
            bat '"C:\\Users\\Sahil Mahadik\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe" build -t asset-allocation-portal:%BUILD_NUMBER% .'
        }
    }
}

stage('Deploy Docker Container') {
    steps {
        timeout(time: 3, unit: 'MINUTES') {
            powershell '''
                $ErrorActionPreference = 'Stop'
                $docker = 'C:\\Users\\Sahil Mahadik\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'
                $image = "asset-allocation-portal:$env:BUILD_NUMBER"

                Write-Host 'Verifying the image...'
                & $docker image inspect $image
                if ($LASTEXITCODE -ne 0) {
                    throw "Image not found: $image"
                }

                Write-Host 'Removing the previous application container...'
                & $docker rm -f asset-portal
                if ($LASTEXITCODE -ne 0) {
                    throw 'Could not remove the previous application container.'
                }

                Write-Host 'Starting the new application container...'
                & $docker run -d `
                    --name asset-portal `
                    --restart unless-stopped `
                    -p 8081:8081 `
                    -e 'SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/assetdb' `
                    $image

                if ($LASTEXITCODE -ne 0) {
                    throw 'Docker could not start the new application container.'
                }

                $ready = $false
                for ($i = 0; $i -lt 30; $i++) {
                    Start-Sleep -Seconds 2
                    try {
                        $response = Invoke-WebRequest `
                            -Uri 'http://127.0.0.1:8081/' `
                            -TimeoutSec 3 -UseBasicParsing

                        if ($response.StatusCode -eq 200) {
                            $ready = $true
                            break
                        }
                    } catch {}
                }

                if (!$ready) {
                    & $docker logs --tail 60 asset-portal
                    throw 'Deployment failed health verification.'
                }

                Write-Host 'Docker deployment successful.'
                & $docker ps --filter 'name=asset-portal'
            '''
        }
    }
}


    post {
        always {
            powershell '''
                $pidFile = Join-Path $env:WORKSPACE 'test-app.pid'

                if (Test-Path $pidFile) {
                    $rawPid = (Get-Content $pidFile -Raw).Trim()
                    $appPid = 0

                    if ([int]::TryParse($rawPid, [ref]$appPid)) {
                        $p = Get-CimInstance Win32_Process `
                            -Filter "ProcessId = $appPid" `
                            -ErrorAction SilentlyContinue

                        if ($p -and
                            $p.Name -eq 'java.exe' -and
                            $p.CommandLine -match 'asset-allocation-portal' -and
                            $p.CommandLine -match '--server.port=8082') {

                            Stop-Process -Id $appPid -Force `
                                -ErrorAction SilentlyContinue
                        }
                    }

                    Remove-Item $pidFile -Force `
                        -ErrorAction SilentlyContinue
                }
            '''
        }

        success {
            echo 'SUCCESS: Build, Selenium tests, and deployment completed.'
        }

        failure {
            echo 'FAILURE: Inspect the failed stage and published test results.'
        }

        aborted {
            echo 'Pipeline was aborted.'
        }
    }
}